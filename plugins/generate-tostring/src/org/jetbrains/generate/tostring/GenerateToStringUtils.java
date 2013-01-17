/*
 * Copyright 2001-2013 the original author or authors.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.generate.tostring.config.FilterPattern;
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

    private GenerateToStringUtils() {}

    /**
     * Filters the list of fields from the class with the given parameters from the {@link org.jetbrains.generate.tostring.config.Config config} settings.
     *
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return fields available for this action after the filter process.
     */
    @NotNull
    public static PsiField[] filterAvailableFields(PsiClass clazz, FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering fields using the pattern: " + pattern);
        List<PsiField> availableFields = new ArrayList<PsiField>();

        // performs til filtering process
        PsiField[] fields = clazz.getFields();
        for (PsiField field : fields) {
            // if the field matches the pattern then it shouldn't be in the list of available fields
            if (!pattern.fieldMatches(field)) {
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
     *
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return methods available for this action after the filter process.
     */
    @NotNull
    public static PsiMethod[] filterAvailableMethods(PsiClass clazz, @NotNull FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering methods using the pattern: " + pattern);
        List<PsiMethod> availableMethods = new ArrayList<PsiMethod>();
        PsiMethod[] methods = clazz.getMethods();
        for (PsiMethod method : methods) {
            // the method should be a getter
            if (!PsiAdapter.isGetterMethod(method)) {
                continue;
            }

            // must not return void
            final PsiType returnType = method.getReturnType();
            if (returnType == null || PsiType.VOID.equals(returnType)) {
                continue;
            }

            // method should be public, non static, non abstract
            if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.STATIC) ||
                method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            // method should not be a getter for an existing field
            String fieldName = PsiAdapter.getGetterFieldName(method);
            if (clazz.findFieldByName(fieldName, false) != null) {
                continue;
            }

            // must not be named toString or getClass
            final String methodName = method.getName();
            if ("toString".equals(methodName) || "getClass".equals(methodName) || "hashCode".equals(methodName)) {
                continue;
            }

            // if the method matches the pattern then it shouldn't be in the list of available methods
            if (pattern.methodMatches(method)) {
                continue;
            }

            if (log.isDebugEnabled())
                log.debug("Adding the method " + methodName + " as there is not a field for this getter");
            availableMethods.add(method);
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
            // code generation error - display velocity error in error dialog so user can identify problem quicker
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
