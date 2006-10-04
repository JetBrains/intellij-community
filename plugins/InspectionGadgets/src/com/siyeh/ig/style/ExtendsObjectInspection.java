/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ExtendsObjectInspection extends ClassInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("extends.object.display.name");
    }

    @NotNull
    public String getID() {
        return "ClassExplicitlyExtendsObject";
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "extends.object.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new ExtendsObjectFix();
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "extends.object.remove.quickfix");
        }

        public void doFix(@NotNull Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement extendClassIdentifier = descriptor.getPsiElement();
            final PsiClass element =
                    (PsiClass)extendClassIdentifier.getParent();
            if(element == null){
                return;
            }
            final PsiReferenceList extendsList = element.getExtendsList();
            if(extendsList == null){
                return;
            }
            final PsiJavaCodeReferenceElement[] referenceElements =
                    extendsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement referenceElement :
                    referenceElements){
                deleteElement(referenceElement);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExtendsObjectVisitor();
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            final PsiClassType[] types = aClass.getExtendsListTypes();
            for (final PsiClassType type : types){
                if (type.equalsToText("java.lang.Object")){
                    registerClassError(aClass);
                }
            }
        }
    }
}