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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodNameSameAsClassNameInspection extends MethodInspection {

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

  @Nullable
  protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
    return new InspectionGadgetsFix[]{new RenameFix(), new InspectionGadgetsFix() {
      protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        PsiElement element = descriptor.getPsiElement().getParent();
        if (!(element instanceof PsiMethod)) return;
        PsiTypeElement returnTypeElement = ((PsiMethod)element).getReturnTypeElement();
        if (returnTypeElement != null) returnTypeElement.delete();
      }

      @NotNull
      public String getName() {
        return InspectionGadgetsBundle.message("make.method.ctr.quickfix");
      }
    }};
  }


    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "method.name.same.as.class.name.problem.descriptor");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodNameSameAsClassNameVisitor();
    }

    private static class MethodNameSameAsClassNameVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down into inner classes
            if (method.isConstructor()) {
                return;
            }
            final String methodName = method.getName();
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String className = containingClass.getName();
            if (className == null) {
                return;
            }
            if (!methodName.equals(className)) {
                return;
            }
            registerMethodError(method);
        }
    }
}