/*
 * Copyright 2001-2012 the original author or authors.
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
package org.jetbrains.java.generate.element;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Element utilities.
 */
public class ElementUtils {

    private ElementUtils() {}

    /**
     * Gets the list of members to be put in the VelocityContext.
     *
     * @param members a list of {@link PsiMember} objects.
     * @param selectedNotNullMembers
     * @param useAccessors
     * @return a filtered list of only the fields as {@link FieldElement} objects.
     */
    public static List<FieldElement> getOnlyAsFieldElements(Collection<? extends PsiMember> members,
                                                            Collection<? extends PsiMember> selectedNotNullMembers,
                                                            boolean useAccessors) {
        List<FieldElement> fieldElementList = new ArrayList<>();

        for (PsiMember member : members) {
            if (member instanceof PsiField) {
                PsiField field = (PsiField) member;
                FieldElement fe = ElementFactory.newFieldElement(field, useAccessors);
                if (selectedNotNullMembers.contains(member)) {
                    fe.setNotNull(true);
                }
                fieldElementList.add(fe);
            }
        }

        return fieldElementList;
    }

    /**
     * Gets the list of members to be put in the VelocityContext.
     *
     * @param members a list of {@link com.intellij.psi.PsiMember} objects.
     * @return a filtered list of only the methods as a {@link MethodElement} objects.
     */
    public static List<MethodElement> getOnlyAsMethodElements(Collection<? extends PsiMember> members) {
        List<MethodElement> methodElementList = new ArrayList<>();

        for (PsiMember member : members) {
            if (member instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) member;
                MethodElement me = ElementFactory.newMethodElement(method);
                methodElementList.add(me);
            }
        }

        return methodElementList;
    }

    /**
     * Gets the list of members to be put in the VelocityContext.
     *
     * @param members                  a list of {@link PsiMember} objects.
     * @param selectedNotNullMembers  a list of @NotNull objects
     * @param useAccessors
     * @return a filtered list of only the methods as a {@link FieldElement} or {@link MethodElement} objects.
     */
    public static List<Element> getOnlyAsFieldAndMethodElements(Collection<? extends PsiMember> members,
                                                                Collection<? extends PsiMember> selectedNotNullMembers,
                                                                boolean useAccessors) {
        List<Element> elementList = new ArrayList<>();

        for (PsiMember member : members) {
            AbstractElement element = null;
            if (member instanceof PsiField) {
              element = ElementFactory.newFieldElement((PsiField) member, useAccessors);
            } else if (member instanceof PsiMethod) {
              element = ElementFactory.newMethodElement((PsiMethod) member);
            }

            if (element != null) {
              if (selectedNotNullMembers.contains(member)) {
                element.setNotNull(true);
              }
              elementList.add(element);
            }
        }
        return elementList;
    }
}
