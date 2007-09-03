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
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class OverridableMethodCallDuringObjectConstructionInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overridable.method.call.in.constructor.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
      return InspectionGadgetsBundle.message(
              "overridable.method.call.in.constructor.problem.descriptor");
    }

    @NotNull
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final PsiElement methodExpression = location.getParent();
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)methodExpression.getParent();
        final PsiClass callClass =
                ClassUtils.getContainingClass(methodCallExpression);
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return InspectionGadgetsFix.EMPTY_ARRAY;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (!containingClass.equals(callClass) ||
                MethodUtils.isOverridden(method)) {
            return InspectionGadgetsFix.EMPTY_ARRAY;
        }
        if (!ClassUtils.isOverridden(containingClass)) {
            return new InspectionGadgetsFix[]{
                    new MakeClassFinalFix(containingClass),
                    new MakeMethodFinalFix(location.getText())};
        } else {
            return new InspectionGadgetsFix[]{
                    new MakeMethodFinalFix(location.getText())};
        }
    }

    private static class MakeClassFinalFix extends InspectionGadgetsFix {

        private final String className;

        MakeClassFinalFix(PsiClass aClass) {
            className = aClass.getName();
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "make.class.final.fix.name", className);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiClass containingClass =
                    PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (containingClass == null) {
                return;
            }
            final PsiModifierList modifierList =
                    containingClass.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
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
            final PsiMember member =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class,
                            PsiClassInitializer.class);
            if (member instanceof PsiClassInitializer) {
                final PsiClassInitializer classInitializer =
                        (PsiClassInitializer)member;
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }
            } else if (member instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod)member;
                if (!isObjectConstructionMethod(method)) {
                    return;
                }
            } else {
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
            final PsiClass containingClass = member.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiMethod calledMethod = 
                    (PsiMethod) methodExpression.resolve();
            if (calledMethod == null || !PsiUtil.canBeOverriden(calledMethod)) {
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            if (calledMethodClass == null ||
                !calledMethodClass.equals(containingClass)) {
                return;
            }
            registerMethodCallError(call);
        }

        public static boolean isObjectConstructionMethod(PsiMethod method) {
            if (method.isConstructor()) {
                return true;
            }
            if (CloneUtils.isClone(method)) {
                return true;
            }
            if (MethodUtils.simpleMethodMatches(method, null, "void",
                    "readObject", "java.io.ObjectInputStream")) {
                return true;
            }
            return MethodUtils.simpleMethodMatches(method, null, "void",
                    "readObjectNoData");
        }
    }
}