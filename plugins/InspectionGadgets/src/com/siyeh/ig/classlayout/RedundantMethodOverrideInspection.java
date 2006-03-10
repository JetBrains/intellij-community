/*
 * Copyright 2005 Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nullable;

public class RedundantMethodOverrideInspection extends MethodInspection {

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.method.override.display.name");
    }

    @Nullable
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "redundant.method.override.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RedundantMethodOverrideFix();
    }

    private static class  RedundantMethodOverrideFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "redundant.method.override.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiElement method = methodNameIdentifier.getParent();
            assert method != null;
            deleteElement(method);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantMethodOverrideVisitor();
    }

    private static class RedundantMethodOverrideVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiMethod[] superMethods = method.findSuperMethods(false);
            if (superMethods.length == 0) {
                return;
            }
            final PsiMethod superMethod = superMethods[0];
            final PsiCodeBlock superBody = superMethod.getBody();
            if (superBody == null) {
                return;
            }
            final PsiModifierList superModifierList =
                    superMethod.getModifierList();
            final PsiModifierList modifierList = method.getModifierList();
            if (!EquivalenceChecker.modifierListsAreEquivalent(
                    modifierList, superModifierList)) {
                return;
            }
            if (!EquivalenceChecker.codeBlocksAreEquivalent(body, superBody)) {
                return;
            }
            registerMethodError(method);
        }
    }
}