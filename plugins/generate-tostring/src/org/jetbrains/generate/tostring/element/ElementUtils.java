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
package org.jetbrains.generate.tostring.element;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import org.jetbrains.generate.tostring.psi.PsiAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Element utilities.
 */
public class ElementUtils {

    private ElementUtils() {
    }

    /**
     * Get's the list of members to be put in the VelocityContext.
     *
     * @param project           Project
     * @param psi               PSI adapter
     * @param members a list of {@link com.intellij.psi.PsiMember} objects.
     * @return a filtered list of only the fields as {@link FieldElement} objects.
     */
    public static List<FieldElement> getOnlyAsFieldElements(Project project, PsiAdapter psi, Collection<PsiMember> members) {
        List<FieldElement> fieldElementList = new ArrayList<FieldElement>();

        for (PsiMember member : members) {
            if (member instanceof PsiField) {
                PsiField field = (PsiField) member;
                FieldElement fe = ElementFactory.newFieldElement(project, field, psi);
                fieldElementList.add(fe);
            }
        }

        return fieldElementList;
    }

    /**
     * Get's the list of members to be put in the VelocityContext.
     *
     * @param psi               PSI adapter
     * @param elementFactory    Element Factory
     * @param members a list of {@link com.intellij.psi.PsiMember} objects.
     * @return a filtered list of only the methods as a {@link MethodElement} objects.
     */
    public static List<MethodElement> getOnlyAsMethodElements(PsiElementFactory elementFactory, PsiAdapter psi, Collection<PsiMember> members) {
        List<MethodElement> methodElementList = new ArrayList<MethodElement>();

        for (PsiMember member : members) {
            if (member instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) member;
                MethodElement me = ElementFactory.newMethodElement(method, elementFactory, psi);
                methodElementList.add(me);
            }
        }

        return methodElementList;
    }

    /**
     * Get's the list of members to be put in the VelocityContext.
     *
     * @param project           Project
     * @param elementFactory    Element Factory
     * @param psi               PSI adapter
     * @param members a list of {@link com.intellij.psi.PsiMember} objects.
     * @return a filtered list of only the methods as a {@link FieldElement} or {@link MethodElement} objects.
     */
    public static List<Element> getOnlyAsFieldAndMethodElements(Project project, PsiElementFactory elementFactory, PsiAdapter psi, Collection<PsiMember> members) {
        List<Element> elementList = new ArrayList<Element>();

        for (PsiMember member : members) {
            if (member instanceof PsiField) {
                PsiField field = (PsiField) member;
                FieldElement fe = ElementFactory.newFieldElement(project, field, psi);
                elementList.add(fe);
            } else if (member instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) member;
                MethodElement me = ElementFactory.newMethodElement(method, elementFactory, psi);
                elementList.add(me);
            }
        }

        return elementList;
    }
    
}
