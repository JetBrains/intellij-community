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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OverridableMethodCallInConstructorInspection
        extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overridable.method.call.in.constructor.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
      return InspectionGadgetsBundle.message(
              "overridable.method.call.in.constructor.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiElement methodExpression = location.getParent();
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)methodExpression.getParent();
        final PsiClass callClass = PsiTreeUtil
                .getParentOfType(methodCallExpression, PsiClass.class);
        final PsiMethod method = methodCallExpression.resolveMethod();
        assert method != null;
        final PsiClass containingClass = method.getContainingClass();
        if (!containingClass.equals(callClass) ||
                MethodUtils.isOverridden(method)) {
            return null;
        }
        return new MakeMethodFinalFix(location.getText());
    }

    private static class MakeMethodFinalFix extends InspectionGadgetsFix {

        private final String methodName;

        MakeMethodFinalFix(String methodName) {
            this.methodName = methodName;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "make.method.final.fix.name", methodName);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiElement methodExpression = methodName.getParent();
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression)methodExpression.getParent();
            final PsiMethod method = methodCall.resolveMethod();
            assert method != null;
            final PsiModifierList modifierList = method.getModifierList();
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverridableMethodCallInConstructorVisitor();
    }

    private static class OverridableMethodCallInConstructorVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (method == null) {
                return;
            }
            if (!method.isConstructor()) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier != null) {
                if (!(qualifier instanceof PsiThisExpression
                        || qualifier instanceof PsiSuperExpression)) {
                    return;
                }
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiMethod calledMethod = 
                    (PsiMethod) methodExpression.resolve();
            if (calledMethod == null) {
                return;
            }
            if (!PsiUtil.canBeOverriden(calledMethod)) {
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            if(calledMethodClass == null){
                return;
            }
            if(!calledMethodClass.equals(containingClass)){
                return;
            }
            registerMethodCallError(call);
        }
    }
}