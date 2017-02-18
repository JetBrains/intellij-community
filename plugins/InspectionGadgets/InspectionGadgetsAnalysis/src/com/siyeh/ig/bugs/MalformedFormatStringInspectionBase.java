/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MalformedFormatStringInspectionBase extends BaseInspection {
  /**
   * @noinspection PublicField
   */
  @NonNls public String additionalClasses = "";

  /**
   * @noinspection PublicField
   */
  @NonNls public String additionalMethods = "";

  final List<String> classNames;
  final List<String> methodNames;

  public MalformedFormatStringInspectionBase() {
    classNames = new ArrayList<>();
    methodNames = new ArrayList<>();
    parseString(additionalClasses, classNames);
    parseString(additionalMethods, methodNames);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(additionalClasses, classNames);
    parseString(additionalMethods, methodNames);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    additionalClasses = formatString(classNames);
    additionalMethods = formatString(methodNames);
    super.writeSettings(node);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("malformed.format.string.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object value = infos[0];
    if (value instanceof Exception) {
      final Exception exception = (Exception)value;
      final String message = exception.getMessage();
      if (message != null) {
        return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.illegal", message);
      }
      return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.malformed");
    }
    final FormatDecode.Validator[] validators = (FormatDecode.Validator[])value;
    final int argumentCount = ((Integer)infos[1]).intValue();
    if (validators.length < argumentCount) {
      return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.many.arguments",
                                             argumentCount, validators.length);
    }
    if (validators.length > argumentCount) {
      return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.few.arguments",
                                             argumentCount, validators.length);
    }
    final PsiType argumentType = (PsiType)infos[2];
    final FormatDecode.Validator validator = (FormatDecode.Validator)infos[3];
    return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.arguments.do.not.match.type",
                                           argumentType.getPresentableText(), validator.getSpecifier());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedFormatStringVisitor();
  }

  private class MalformedFormatStringVisitor extends BaseInspectionVisitor {

    private int findFirstStringArgumentIndex(PsiExpression[] expressions) {
      for (int i = 0, length = expressions.length; i < length; i++) {
        final PsiExpression expression = expressions[i];
        if (ExpressionUtils.hasStringType(expression)) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!FormatUtils.isFormatCall(expression, methodNames, classNames)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int formatArgumentIndex = findFirstStringArgumentIndex(arguments);
      if (formatArgumentIndex < 0) {
        return;
      }
      final PsiExpression formatArgument = arguments[formatArgumentIndex];
      if (!ExpressionUtils.hasStringType(formatArgument) || !PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }
      final PsiType formatType = formatArgument.getType();
      if (formatType == null) {
        return;
      }
      final String value = (String)ConstantExpressionUtil.computeCastTo(formatArgument, formatType);
      if (value == null) {
        return;
      }
      final int argumentCount = arguments.length - (formatArgumentIndex + 1);
      final FormatDecode.Validator[] validators;
      try {
        validators = FormatDecode.decode(value, argumentCount);
      }
      catch (FormatDecode.IllegalFormatException e) {
        registerError(formatArgument, e);
        return;
      }
      if (validators.length != argumentCount) {
        if (argumentCount == 1) {
          final PsiExpression argument = arguments[formatArgumentIndex + 1];
          final PsiType argumentType = argument.getType();
          if (argumentType instanceof PsiArrayType) {
            return;
          }
        }
        registerMethodCallError(expression, validators, Integer.valueOf(argumentCount));
        return;
      }
      for (int i = 0; i < validators.length; i++) {
        final FormatDecode.Validator validator = validators[i];
        final PsiExpression argument = arguments[i + formatArgumentIndex + 1];
        final PsiType argumentType = argument.getType();
        if (argumentType == null) {
          continue;
        }
        if (validator != null && !validator.valid(argumentType)) {
          registerError(argument, validators, Integer.valueOf(argumentCount), argumentType, validator);
          return;
        }
      }
    }
  }
}