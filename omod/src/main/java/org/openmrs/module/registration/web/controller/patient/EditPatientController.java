/**
 *  Copyright 2008 Society for Health Information Systems Programmes, India (HISP India)
 *
 *  This file is part of Registration module.
 *
 *  Registration module is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.

 *  Registration module is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Registration module.  If not, see <http://www.gnu.org/licenses/>.
 *
 **/

package org.openmrs.module.registration.web.controller.patient;

import java.io.IOException;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.DocumentException;
import org.jaxen.JaxenException;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.context.Context;
import org.openmrs.module.hospitalcore.HospitalCoreService;
import org.openmrs.module.hospitalcore.util.HospitalCoreUtils;
import org.openmrs.module.registration.includable.validator.attribute.PatientAttributeValidatorService;
import org.openmrs.module.registration.util.RegistrationConstants;
import org.openmrs.module.registration.util.RegistrationUtils;
import org.openmrs.module.registration.web.controller.util.PatientModel;
import org.openmrs.module.registration.web.controller.util.RegistrationWebUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller("RegistrationEditPatientController")
@RequestMapping("/module/registration/editPatient.form")
public class EditPatientController {

	private static Log logger = LogFactory.getLog(EditPatientController.class);

	@RequestMapping(method = RequestMethod.GET)
	public String showForm(@RequestParam("patientId") Integer patientId,
			Model model) throws JaxenException, DocumentException, IOException,
			ParseException {
		HospitalCoreService hospitalCoreService = (HospitalCoreService) Context.getService(HospitalCoreService.class);
		Patient patient = Context.getPatientService().getPatient(patientId);
		PatientModel patientModel = new PatientModel(patient);
		model.addAttribute("patient", patientModel);
		RegistrationWebUtils.getAddressDta(model);
		model.addAttribute("religionList", RegistrationWebUtils.getReligionConcept());
		PersonAttributeType personAttributeReligion=hospitalCoreService.getPersonAttributeTypeByName("Religion");
		model.addAttribute("personAttributeReligion", personAttributeReligion);
		PersonAttributeType personAttributeChiefdom=hospitalCoreService.getPersonAttributeTypeByName("Chiefdom");
		model.addAttribute("personAttributeChiefdom", personAttributeChiefdom);
		
		//
		model.addAttribute(
				"payingCategory",
				RegistrationWebUtils
						.getSubConceptsWithName(RegistrationConstants.CONCEPT_NAME_PAYING_CATEGORY));
		model.addAttribute(
				"nonPayingCategory",
				RegistrationWebUtils
						.getSubConceptsWithName(RegistrationConstants.CONCEPT_NAME_NONPAYING_CATEGORY));
		model.addAttribute(
				"specialScheme",
				RegistrationWebUtils
						.getSubConceptsWithName(RegistrationConstants.CONCEPT_NAME_SPECIAL_SCHEME));
		model.addAttribute(
				"universities",
				RegistrationWebUtils
						.getSubConceptsWithName(RegistrationConstants.CONCEPT_NAME_LIST_OF_UNIVERSITIES));
		Map<Integer, String> payingCategoryMap = new LinkedHashMap<Integer, String>();
		Concept payingCategory = Context.getConceptService().getConcept(RegistrationConstants.CONCEPT_NAME_PAYING_CATEGORY);
		for (ConceptAnswer ca : payingCategory.getAnswers()) {
			payingCategoryMap.put(ca.getAnswerConcept().getConceptId(), ca.getAnswerConcept().getName().getName());
		}
		Map<Integer, String> nonPayingCategoryMap = new LinkedHashMap<Integer, String>();
		Concept nonPayingCategory = Context.getConceptService().getConcept(RegistrationConstants.CONCEPT_NAME_NONPAYING_CATEGORY);
		for (ConceptAnswer ca : nonPayingCategory.getAnswers()) {
			nonPayingCategoryMap.put(ca.getAnswerConcept().getConceptId(), ca.getAnswerConcept().getName().getName());
		}
		Map<Integer, String> specialSchemeMap = new LinkedHashMap<Integer, String>();
		Concept specialScheme = Context.getConceptService().getConcept(RegistrationConstants.CONCEPT_NAME_SPECIAL_SCHEME);
		for (ConceptAnswer ca : specialScheme.getAnswers()) {
			specialSchemeMap.put(ca.getAnswerConcept().getConceptId(), ca.getAnswerConcept().getName().getName());
		}
		model.addAttribute("payingCategoryMap", payingCategoryMap);
		model.addAttribute("nonPayingCategoryMap", nonPayingCategoryMap);
		model.addAttribute("specialSchemeMap", specialSchemeMap);
		return "/module/registration/patient/editPatient";
	}

	@RequestMapping(method = RequestMethod.POST)
	public String savePatient(@RequestParam("patientId") Integer patientId,
			HttpServletRequest request, Model model) throws ParseException {

		Patient patient = Context.getPatientService().getPatient(patientId);

		// list all parameter submitted
		Map<String, String> parameters = RegistrationWebUtils
				.optimizeParameters(request);
		logger.info("Submited parameters: " + parameters);

		try {
			// update patient
			Patient updatedPatient = generatePatient(patient, parameters);
			patient = Context.getPatientService().savePatient(updatedPatient);

			// update patient attribute
			updatedPatient = setAttributes(patient, parameters);
			patient = Context.getPatientService().savePatient(updatedPatient);
			RegistrationUtils.savePatientSearch(patient);

			model.addAttribute("status", "success");
			logger.info(String.format("Updated patient [id=%s]",
					patient.getId()));
		} catch (Exception e) {
			model.addAttribute("status", "error");
			model.addAttribute("message", e.getMessage());
		}

		return "/module/registration/patient/savePatient";
	}

	/**
	 * Generate Patient From Parameters
	 * 
	 * @param parameters
	 * @return
	 * @throws ParseException
	 */
	private Patient generatePatient(Patient patient,
			Map<String, String> parameters) throws ParseException {

		// get person name
		if (!StringUtils.isBlank(parameters
				.get(RegistrationConstants.FORM_FIELD_PATIENT_SURNAME))
				&& !StringUtils
						.isBlank(parameters
								.get(RegistrationConstants.FORM_FIELD_PATIENT_FIRSTNAME))) {
			RegistrationUtils
					.getPersonName(
							patient.getPersonName(),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_SURNAME),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_FIRSTNAME),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_OTHERNAME));
		}

		// get birthdate
		if (!StringUtils.isBlank(parameters
				.get(RegistrationConstants.FORM_FIELD_PATIENT_BIRTHDATE))) {
			patient.setBirthdate(RegistrationUtils.parseDate(parameters
					.get(RegistrationConstants.FORM_FIELD_PATIENT_BIRTHDATE)));
			if (parameters
					.get(RegistrationConstants.FORM_FIELD_PATIENT_BIRTHDATE_ESTIMATED)
					.contains("true")) {
				patient.setBirthdateEstimated(true);
			}
		}

		// get gender
		if (!StringUtils.isBlank(parameters
				.get(RegistrationConstants.FORM_FIELD_PATIENT_GENDER))) {
			patient.setGender(parameters
					.get(RegistrationConstants.FORM_FIELD_PATIENT_GENDER));
		}

		// get address
		if (!StringUtils
				.isBlank(parameters
						.get(RegistrationConstants.FORM_FIELD_PATIENT_ADDRESS_DISTRICT))) {
			RegistrationUtils
					.getPersonAddress(
							patient.getPersonAddress(),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_ADDRESS_POSTALADDRESS),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_ADDRESS_DISTRICT),
							parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_ADDRESS_UPAZILA),
									parameters
									.get(RegistrationConstants.FORM_FIELD_PATIENT_ADDRESS_LOCATION));
		}

		return patient;
	}

	private Patient setAttributes(Patient patient,
			Map<String, String> attributes) throws Exception {
		PatientAttributeValidatorService validator = new PatientAttributeValidatorService();
		Map<String, Object> parameters = HospitalCoreUtils.buildParameters(
				"patient", patient, "attributes", attributes);
		String validateResult = validator.validate(parameters);
		logger.info("Attirubte validation: " + validateResult);
		if (StringUtils.isBlank(validateResult)) {
			for (String name : attributes.keySet()) {
				if ((name.contains(".attribute."))
						&& (!StringUtils.isBlank(attributes.get(name)))) {
					String[] parts = name.split("\\.");
					String idText = parts[parts.length - 1];
					Integer id = Integer.parseInt(idText);
					PersonAttribute attribute = RegistrationUtils
							.getPersonAttribute(id, attributes.get(name));
					patient.addAttribute(attribute);
				}
			}
		} else {
			throw new Exception(validateResult);
		}

		return patient;
	}
}
