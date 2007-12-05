/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class RedundantImplementsInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "RedundantInterfaceDeclaration";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.implements.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "redundant.implements.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RedundantImplementsFix();
    }

    private static class RedundantImplementsFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "redundant.implements.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement implementReference = descriptor.getPsiElement();
            deleteElement(implementReference);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantImplementsVisitor();
    }

    private static class RedundantImplementsVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            if (aClass.isAnnotationType()) {
                return;
            }
            if (aClass.isInterface()) {
                checkInterface(aClass);
            } else {
                checkConcreteClass(aClass);
            }
        }

        private void checkInterface(PsiClass aClass) {
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] extendsElements =
                    extendsList.getReferenceElements();
            for (final PsiJavaCodeReferenceElement implementsElement :
                    extendsElements) {
                final PsiElement referent = implementsElement.resolve();
                if (referent instanceof PsiClass) {
                    final PsiClass implementedClass = (PsiClass)referent;
                    checkExtendedInterface(implementedClass,
                            implementsElement, extendsElements);
                }
            }
        }

        private void checkConcreteClass(PsiClass aClass) {
            final PsiReferenceList extendsList = aClass.getExtendsList();
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if (extendsList == null || implementsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] extendsElements =
                    extendsList.getReferenceElements();
            final PsiJavaCodeReferenceElement[] implementsElements =
                    implementsList.getReferenceElements();
            for (final PsiJavaCodeReferenceElement implementsElement :
                    implementsElements) {
                final PsiElement referent = implementsElement.resolve();
                if (!(referent instanceof PsiClass)) {
                    continue;
                }
                final PsiClass implementedClass = (PsiClass)referent;
                checkImplementedClass(implementedClass, implementsElement,
                        extendsElements, implementsElements);
            }
        }

        private void checkImplementedClass(
                PsiClass implementedClass,
                PsiJavaCodeReferenceElement implementsElement,
                PsiJavaCodeReferenceElement[] extendsElements,
                PsiJavaCodeReferenceElement[] implementsElements) {
            for (final PsiJavaCodeReferenceElement extendsElement :
                    extendsElements) {
                final PsiElement extendsReferent = extendsElement.resolve();
                if (!(extendsReferent instanceof PsiClass)) {
                    continue;
                }
                final PsiClass extendedClass = (PsiClass)extendsReferent;
                if (extendedClass.isInheritor(implementedClass, true)) {
                    registerError(implementsElement);
                    return;
                }
            }
            for (final PsiJavaCodeReferenceElement testImplementElement :
                    implementsElements) {
                if (testImplementElement.equals(implementsElement)) {
                    continue;
                }
                final PsiElement implementsReferent =
                        testImplementElement.resolve();
                if (!(implementsReferent instanceof PsiClass)) {
                    continue;
                }
                final PsiClass testImplementedClass =
                        (PsiClass)implementsReferent;
                if (testImplementedClass.isInheritor(implementedClass, true)) {
                    registerError(implementsElement);
                    return;
                }
            }
        }

        private void checkExtendedInterface(
                PsiClass implementedClass,
                PsiJavaCodeReferenceElement implementsElement,
                PsiJavaCodeReferenceElement[] extendsElements) {
            for (final PsiJavaCodeReferenceElement testImplementElement :
                    extendsElements) {
                if (testImplementElement.equals(implementsElement)) {
                    continue;
                }
                final PsiElement implementsReferent =
                        testImplementElement.resolve();
                if (!(implementsReferent instanceof PsiClass)) {
                    continue;
                }
                final PsiClass testImplementedClass =
                        (PsiClass)implementsReferent;
                if (testImplementedClass.isInheritor(implementedClass, true)) {
                    registerError(implementsElement);
                    return;
                }
            }
        }
    }
}