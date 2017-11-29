/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class MakePublicStaticVoidFix extends InspectionGadgetsFix {
  private final String myName;
  private final boolean myMakeStatic;
  private final String myNewVisibility;

  public MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic) {
    this(method, makeStatic, PsiModifier.PUBLIC);
  }

  public MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic, @PsiModifier.ModifierConstant String newVisibility) {
    String presentableVisibility = VisibilityUtil.getVisibilityString(newVisibility);
    myName = "Change signature of \'" +
             PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_MODIFIERS |
                                        PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE, PsiFormatUtilBase.SHOW_TYPE) +
             "\' to \'" + (presentableVisibility.isEmpty() ? "" : presentableVisibility + " ") + (makeStatic ? "static " : "") +
             "void " + method.getName() + "()\'";
    myMakeStatic = makeStatic;
    myNewVisibility = newVisibility;
  }

  @Override
  protected void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
    if (method != null) {
      ChangeSignatureProcessor csp =
        new ChangeSignatureProcessor(project, method, false, myNewVisibility, method.getName(), PsiType.VOID,
                                     new ParameterInfoImpl[0]) {
          @Override
          protected void performRefactoring(@NotNull UsageInfo[] usages) {
            super.performRefactoring(usages);
            PsiUtil.setModifierProperty(method, PsiModifier.STATIC, myMakeStatic);
          }
        };
      csp.run();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Fix modifiers";
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }
}
