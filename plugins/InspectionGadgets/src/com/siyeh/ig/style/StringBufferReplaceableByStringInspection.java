/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    if (element instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("new.string.buffer.replaceable.by.string.problem.descriptor");
    }
    final String typeText = ((PsiType)infos[1]).getPresentableText();
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.problem.descriptor", typeText);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String typeText = ((PsiType)infos[1]).getCanonicalText();
    return new StringBufferReplaceableByStringFix(CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(typeText));
  }

  private static class StringBufferReplaceableByStringFix extends InspectionGadgetsFix {

    private final boolean isStringBuilder;

    private StringBufferReplaceableByStringFix(boolean isStringBuilder) {
      this.isStringBuilder = isStringBuilder;
    }

    @NotNull
    @Override
    public String getName() {
      if (isStringBuilder) {
        return InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.quickfix");
      }
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiVariable)) {
        if (parent instanceof PsiNewExpression) {
          final PsiNewExpression newExpression = (PsiNewExpression)parent;
          final PsiExpression stringBuilderExpression = getCompleteExpression(newExpression);
          final StringBuilder stringExpression = buildStringExpression(stringBuilderExpression, new StringBuilder());
          if (stringExpression != null && stringBuilderExpression != null) {
            replaceExpression(stringBuilderExpression, stringExpression.toString());
          }
          return;
        }
        return;
      }
      final PsiVariable variable = (PsiVariable)parent;
      final PsiTypeElement originalTypeElement = variable.getTypeElement();
      if (originalTypeElement == null) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }
      final StringBuilder stringExpression = buildStringExpression(initializer, new StringBuilder());
      if (stringExpression == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiClassType javaLangString = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, variable.getResolveScope());
      final PsiTypeElement typeElement = factory.createTypeElement(javaLangString);
      replaceExpression(initializer, stringExpression.toString());
      originalTypeElement.replace(typeElement);
    }

    @Nullable
    private static StringBuilder buildStringExpression(PsiExpression initializer, StringBuilder result) {
      if (initializer instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)initializer;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList ==  null) {
          return null;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length == 1) {
          final PsiExpression argument = arguments[0];
          final PsiType type = argument.getType();
          if (!PsiType.INT.equals(type)) {
            result.append(argument.getText());
          }
        }
        final PsiElement parent = initializer.getParent();
        if (result.length() == 0 && parent instanceof PsiVariable) {
          result.append("\"\"");
        }
        return result;
      }
      else if (initializer instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)initializer;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        result = buildStringExpression(qualifier, result);
        if (result == null) {
          return null;
        }
        if ("toString".equals(methodExpression.getReferenceName())) {
          if (result.length() == 0) {
            result.append("\"\"");
          }
        }
        else {
          final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
          final PsiExpression[] arguments = argumentList.getExpressions();
          if (arguments.length != 1) {
            return null;
          }
          final PsiExpression argument = arguments[0];
          if (result.length() != 0) {
            result.append('+');
            if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
              result.append('(').append(argument.getText()).append(')');
            }
            else {
              result.append(argument.getText());
            }
          }
          else {
            final PsiType type = argument.getType();
            if (type instanceof PsiPrimitiveType) {
              result.append("String.valueOf(").append(argument.getText()).append(")");
            }
            else {
              if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
                result.append('(').append(argument.getText()).append(')');
              }
              else {
                result.append(argument.getText());
              }
            }
          }
        }
        return result;
      }
      return null;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringVisitor();
  }

  private static class StringBufferReplaceableByStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (codeBlock == null) {
        return;
      }
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
          !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }
      if (!isNewStringBufferOrStringBuilder(initializer)) {
        return;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, codeBlock)) {
        return;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, codeBlock)) {
        return;
      }
      if (VariableAccessUtils.variableIsReturned(variable, codeBlock)) {
        return;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, codeBlock)) {
        return;
      }
      if (variableIsModified(variable, codeBlock)) {
        return;
      }
      registerVariableError(variable, variable, type);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
          !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
        return;
      }
      final PsiExpression completeExpression = getCompleteExpression(expression);
      if (completeExpression == null) {
        return;
      }
      registerNewExpressionError(expression, expression, type);
    }

    public static boolean variableIsModified(PsiVariable variable, PsiElement context) {
      final VariableIsModifiedVisitor visitor = new VariableIsModifiedVisitor(variable);
      context.accept(visitor);
      return visitor.isModified();
    }

    private static boolean isNewStringBufferOrStringBuilder(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      else if (expression instanceof PsiNewExpression) {
        return true;
      }
      else if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        if (!isAppend(methodCallExpression)) {
          return false;
        }
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        return isNewStringBufferOrStringBuilder(qualifier);
      }
      return false;
    }

    public static boolean isAppend(PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      return "append".equals(methodName);
    }
  }

  @Nullable
  private static PsiExpression getCompleteExpression(PsiNewExpression expression) {
    PsiElement completeExpression = expression;
    boolean found = false;
    while (true) {
      final PsiElement parent = completeExpression.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        break;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        break;
      }
      final String name = referenceExpression.getReferenceName();
      if ("append".equals(name)) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
          return null;
        }
      }
      else {
        if (!"toString".equals(name)) {
          return null;
        }
        found = true;
      }
      completeExpression = grandParent;
      if (found) {
        return (PsiExpression)completeExpression;
      }
    }
    return null;
  }
}
