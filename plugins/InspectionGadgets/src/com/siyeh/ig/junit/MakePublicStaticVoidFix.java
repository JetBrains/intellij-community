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
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.VisibilityUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class MakePublicStaticVoidFix extends InspectionGadgetsFix {
  private final @IntentionName String myName;
  private final boolean myMakeStatic;
  private final String myNewVisibility;

  MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic) {
    this(method, makeStatic, PsiModifier.PUBLIC);
  }

  MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic, @PsiModifier.ModifierConstant String newVisibility) {
    final int formatOptions = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_MODIFIERS
                              | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE;
    final String methodBefore = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, formatOptions, PsiFormatUtilBase.SHOW_TYPE);

    String presentableVisibility = VisibilityUtil.getVisibilityString(newVisibility);
    final @NonNls String methodAfter = (presentableVisibility.isEmpty() ? presentableVisibility : presentableVisibility + " ") +
                                       (makeStatic ? "static " : "") +
                                       "void " + method.getName() + "()";

    myName = InspectionGadgetsBundle.message("make.public.static.void.fix.name", methodBefore, methodAfter);
    myMakeStatic = makeStatic;
    myNewVisibility = newVisibility;
  }

  @Override
  protected void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
    if (method != null) {
      var provider = JavaSpecialRefactoringProvider.getInstance();
      var csp = provider.getChangeSignatureProcessorWithCallback(project, method, false, myNewVisibility, method.getName(), PsiType.VOID,
                                                                 new ParameterInfoImpl[0], true, infos -> {
          PsiUtil.setModifierProperty(method, PsiModifier.STATIC, myMakeStatic);
        });
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
    return InspectionGadgetsBundle.message("make.public.static.void.fix.family.name");
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }
}
