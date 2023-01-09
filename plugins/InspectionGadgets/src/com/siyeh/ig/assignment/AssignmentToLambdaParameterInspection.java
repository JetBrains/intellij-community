/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentToLambdaParameterInspection extends BaseAssignmentToParameterInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.lambda.parameter.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTransformationOfOriginalParameter", InspectionGadgetsBundle.message(
        "assignment.to.method.parameter.ignore.transformation.option")));
  }

  @Override
  protected boolean isApplicable(PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiLambdaExpression)) {
      return false;
    }
    return !(parameter.getType() instanceof PsiLambdaParameterType);
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel8OrHigher(file);
  }
}
