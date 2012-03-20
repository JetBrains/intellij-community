/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import org.jetbrains.generate.tostring.config.FilterPattern;
import org.jetbrains.generate.tostring.element.ElementFactory;
import org.jetbrains.generate.tostring.element.FieldElement;
import org.jetbrains.generate.tostring.element.MethodElement;
import org.jetbrains.generate.tostring.exception.GenerateCodeException;
import org.jetbrains.generate.tostring.exception.PluginException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for GenerationToStringAction and the inspections.
 */
public class GenerateToStringUtils {
    private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringUtils");

    /**
     * Private constructor.
     */
    private GenerateToStringUtils() {
    }

    /**
     * Filters the list of fields from the class with the given parameters from the {@link org.jetbrains.generate.tostring.config.Config config} settings.
     *
     * @param project        Project
     * @param psi            PSI adapter
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return fields avaiable for this action after the filter process.
     */
    public static PsiField[] filterAvailableFields(Project project, PsiAdapter psi, PsiClass clazz, FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering fields using the pattern: " + pattern);
        List<PsiField> availableFields = new ArrayList<PsiField>();

        // performs til filtering process
      PsiField[] fields = clazz.getFields();
        for (PsiField field : fields) {
            FieldElement fe = ElementFactory.newFieldElement(project, field, psi);
            if (log.isDebugEnabled()) log.debug("Field being filtered: " + fe);

            // if the field matches the pattern then it shouldn't be in the list of avaialble fields
            if (!fe.applyFilter(pattern)) {
                availableFields.add(field);
            }
        }

        return availableFields.toArray(new PsiField[availableFields.size()]);
    }

    /**
     * Filters the list of methods from the class to be
     * <ul>
     * <li/>a getter method (java bean compliant)
     * <li/>should not be a getter for an existing field
     * <li/>public, non static, non abstract
     * <ul/>
     *
     * @param psi            PSI adapter
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return methods avaiable for this action after the filter process.
     */
    public static PsiMethod[] filterAvailableMethods(PsiAdapter psi, PsiClass clazz, FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering methods using the pattern: " + pattern);
        List<PsiMethod> availableMethods = new ArrayList<PsiMethod>();
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory();

      PsiMethod[] methods = clazz.getMethods();
        for (PsiMethod method : methods) {

            MethodElement me = ElementFactory.newMethodElement(method, elementFactory, psi);
            if (log.isDebugEnabled()) log.debug("Method being filtered: " + me);

            // the method should be a getter
            if (!me.isGetter()) {
                continue;
            }

            // must not return void
            if (me.isReturnTypeVoid()) {
                continue;
            }

            // method should be public, non static, non abstract
            if (!me.isModifierPublic() || me.isModifierStatic() || me.isModifierAbstract()) {
                continue;
            }

            // method should not be a getter for an existing field
            if (clazz.findFieldByName(me.getFieldName(), false) != null) {
                continue;
            }

            // must not be named toString or getClass
            if ("toString".equals(me.getMethodName()) || "getClass".equals(me.getMethodName())) {
                continue;
            }

            // if the method matches the pattern then it shouldn't be in the list of avaialble methods
            if (!me.applyFilter(pattern)) {
                if (log.isDebugEnabled())
                    log.debug("Adding the method " + method.getName() + " as there is not a field for this getter");
                availableMethods.add(method);
            }
        }

        return availableMethods.toArray(new PsiMethod[availableMethods.size()]);
    }

    /**
     * Handles any exception during the executing on this plugin.
     *
     * @param project PSI project
     * @param e       the caused exception.
     * @throws RuntimeException is thrown for severe exceptions
     */
    public static void handleException(Project project, Exception e) throws RuntimeException {
        log.info(e);

        if (e instanceof GenerateCodeException) {
            // code generation error - display velocity errror in error dialog so user can identify problem quicker
            Messages.showMessageDialog(project, "Velocity error generating code - see IDEA log for more details (stacktrace should be in idea.log):\n" + e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof PluginException) {
            // plugin related error - could be recoverable.
            Messages.showMessageDialog(project, "A PluginException was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof RuntimeException) {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw (RuntimeException) e; // throw to make IDEA alert user
        } else {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw new RuntimeException(e); // rethrow as runtime to make IDEA alert user
        }
    }

    /**
     * Combines the two lists into one list of members.
     *
     * @param filteredFields  fields to be included in the dialog
     * @param filteredMethods methods to be included in the dialog
     * @return the combined list
     */
    public static PsiElementClassMember[] combineToClassMemberList(PsiField[] filteredFields, PsiMethod[] filteredMethods) {
        PsiElementClassMember[] members = new PsiElementClassMember[filteredFields.length + filteredMethods.length];

        // first add fields
        for (int i = 0; i < filteredFields.length; i++) {
            members[i] = new PsiFieldMember(filteredFields[i]);
        }

        // then add methods
        for (int i = 0; i < filteredMethods.length; i++) {
            members[filteredFields.length + i] = new PsiMethodMember(filteredMethods[i]);
        }

        return members;
    }

    /**
     * Converts the list of {@link PsiElementClassMember} to {PsiMember} objects.
     *
     * @param classMemberList  list of {@link PsiElementClassMember}
     * @return a list of {PsiMember} objects. 
     */
    public static List<PsiMember> convertClassMembersToPsiMembers(List<PsiElementClassMember> classMemberList) {
        List<PsiMember> psiMemberList = new ArrayList<PsiMember>();

        for (PsiElementClassMember classMember : classMemberList) {
            psiMemberList.add(classMember.getElement());
        }

        return psiMemberList;
    }
}
