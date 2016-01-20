/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NullArgumentToVariableArgMethodInspection extends BaseInspection {

  @NotNull
  @Override
  public String getID() {
    return "ConfusingArgumentToVarargsMethod";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "NullArgumentToVariableArgMethod"; // old suppressions should keep working
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("null.argument.to.var.arg.method.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("null.argument.to.var.arg.method.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[0];
    final PsiType type1 = (PsiType)infos[1];
    final PsiType type2 = (PsiType)infos[2];
    return new InspectionGadgetsFix[] {
      new DelegatingFix(new AddTypeCastFix(type1, argument)),
      new DelegatingFix(new AddTypeCastFix(type2, argument))
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NullArgumentToVariableArgVisitor();
  }

  private static class NullArgumentToVariableArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final PsiType type = lastArgument.getType();
      final boolean checkArray;
      if (PsiType.NULL.equals(type)) {
        checkArray = false;
      }
      else if (type instanceof PsiArrayType) {
        checkArray = true;
      }
      else {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != arguments.length) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (!lastParameter.isVarArgs()) {
        return;
      }
      final PsiType type1 = lastParameter.getType();
      if (!(type1 instanceof PsiEllipsisType)) {
        return;
      }
      final PsiEllipsisType ellipsisType = (PsiEllipsisType)type1;
      final PsiType arrayType = ellipsisType.toArrayType();
      if (checkArray) {
        if (arrayType.equals(type) || !arrayType.isAssignableFrom(type)) {
          return;
        }
      }
      registerError(lastArgument, lastArgument, ellipsisType.getComponentType(), arrayType);
    }
  }
}