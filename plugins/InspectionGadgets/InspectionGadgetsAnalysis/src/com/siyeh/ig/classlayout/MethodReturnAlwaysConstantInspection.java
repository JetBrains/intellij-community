/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodInheritanceUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MethodReturnAlwaysConstantInspection extends BaseGlobalInspection {

  private static final Key<Boolean> ALWAYS_CONSTANT =
    Key.create("ALWAYS_CONSTANT");

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.return.always.constant.display.name");
  }

  @Override
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager,
    @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefMethod)) {
      return null;
    }
    final RefMethod refMethod = (RefMethod)refEntity;
    final Boolean alreadyProcessed = refMethod.getUserData(ALWAYS_CONSTANT);
    if (alreadyProcessed != null && alreadyProcessed.booleanValue()) {
      return null;
    }
    if (!(refMethod.getElement() instanceof PsiMethod)) {
      return null;
    }
    final PsiMethod method = (PsiMethod)refMethod.getElement();
    if (method.getBody() == null) {
      return null;     //we'll catch it on another method
    }
    if (!alwaysReturnsConstant(method)) {
      return null;
    }
    final Set<RefMethod> siblingMethods =
      MethodInheritanceUtils.calculateSiblingMethods(refMethod);
    for (RefMethod siblingMethod : siblingMethods) {
      final PsiMethod siblingPsiMethod =
        (PsiMethod)siblingMethod.getElement();
      if (method.getBody() != null &&
          !alwaysReturnsConstant(siblingPsiMethod)) {
        return null;
      }
    }
    final List<ProblemDescriptor> out = new ArrayList<>();
    for (RefMethod siblingRefMethod : siblingMethods) {
      final PsiMethod siblingMethod =
        (PsiMethod)siblingRefMethod.getElement();
      final PsiIdentifier identifier = siblingMethod.getNameIdentifier();
      if (identifier == null) {
        continue;
      }
      out.add(manager.createProblemDescriptor(identifier,
                                              InspectionGadgetsBundle.message(
                                                "method.return.always.constant.problem.descriptor"), false, null,
                                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      siblingRefMethod.putUserData(ALWAYS_CONSTANT,
                                   Boolean.valueOf(true));
    }
    return out.toArray(new ProblemDescriptor[out.size()]);
  }

  private static boolean alwaysReturnsConstant(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
    if (!(statement instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiReturnStatement returnStatement =
      (PsiReturnStatement)statement;
    final PsiExpression value = returnStatement.getReturnValue();
    return value != null && PsiUtil.isConstantExpression(value);
  }
}
