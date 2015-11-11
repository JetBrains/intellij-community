/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StringBufferReplaceableByStringInspection extends StringBufferReplaceableByStringInspectionBase {

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

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'String'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiVariable)) {
        if (parent instanceof PsiNewExpression) {
          final PsiExpression stringBuilderExpression = getCompleteExpression(parent);
          final StringBuilder stringExpression = buildStringExpression(stringBuilderExpression, new StringBuilder());
          if (stringExpression != null && stringBuilderExpression != null) {
            PsiReplacementUtil.replaceExpression(stringBuilderExpression, stringExpression.toString());
          }
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
      final StringBuilder builder;
      if (isAppendCall(initializer)) {
        builder = buildStringExpression(initializer, new StringBuilder());
        if (builder == null) {
          return;
        }
      } else if (initializer instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)initializer;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          return;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length == 0 || PsiType.INT.equals(arguments[0].getType())) {
          builder = new StringBuilder();
        } else {
          builder = new StringBuilder(arguments[0].getText());
        }
      } else {
        return;
      }
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (codeBlock == null) {
        return;
      }
      final StringBuildingVisitor visitor = new StringBuildingVisitor(variable, builder);
      codeBlock.accept(visitor);
      if (visitor.hadProblem()) {
        return;
      }
      final List<PsiMethodCallExpression> expressions = visitor.getExpressions();
      final String expression = builder.toString().trim();
      final PsiMethodCallExpression lastExpression = expressions.get(expressions.size() - 1);
      final boolean useVariable = expression.contains("\n") && !isVariableInitializer(lastExpression);
      if (useVariable) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
        if (statement == null) {
          return;
        }
        final String modifier = CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS ? "final " : "";
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiStatement newStatement =
          factory.createStatementFromText(modifier + CommonClassNames.JAVA_LANG_STRING + ' ' + variable.getName() + '=' +
                                          expression + ';', variable);
        codeBlock.addBefore(newStatement, statement);
      }
      variable.delete();
      for (int i = 0, size = expressions.size() - 1; i < size; i++) {
        expressions.get(i).getParent().delete();
      }
      if (useVariable) {
        PsiReplacementUtil.replaceExpression(lastExpression, variable.getName());
      }
      else {
        PsiReplacementUtil.replaceExpression(lastExpression, expression);
      }
    }

    private static boolean isVariableInitializer(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)parent;
      final PsiExpression initializer = variable.getInitializer();
      return initializer == expression;
    }

    @Nullable
    private static StringBuilder buildStringExpression(PsiElement element, @NonNls StringBuilder result) {
      if (element instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)element;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          return null;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length == 1) {
          final PsiExpression argument = arguments[0];
          final PsiType type = argument.getType();
          if (!PsiType.INT.equals(type)) {
            if (type != null && type.equalsToText("java.lang.CharSequence")) {
              result.append("String.valueOf(").append(argument.getText()).append(')');
            }
            else if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
              result.append('(').append(argument.getText()).append(')');
            }
            else {
              result.append(argument.getText());
            }
          }
        }
        return result;
      }
      for (PsiElement child : element.getChildren()) {
        if (child instanceof PsiExpressionList) {
          continue;
        }
        if (buildStringExpression(child, result) == null) {
          return null;
        }
      }

      if (element instanceof PsiWhiteSpace) {
        if (element.getText().contains("\n") && result.length() > 0) {
          result.append('\n'); // keep line break structure
        }
      }
      else if (element instanceof PsiComment) {
        result.append(element.getText()); // keep comments
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();

        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String referenceName = methodExpression.getReferenceName();
        if ("toString".equals(referenceName)) {
          if (result.length() == 0) {
            result.append("\"\"");
          }
        }
        else if ("append".equals(referenceName)){
          final PsiExpression[] arguments = argumentList.getExpressions();
          if (arguments.length == 0) {
            return null;
          }
          if (arguments.length > 1) {
            if (result.length() != 0) {
              insertPlus(result);
            }
            result.append("String.valueOf").append(argumentList.getText());
            return result;
          }
          final PsiExpression argument = arguments[0];
          final PsiType type = argument.getType();
          final String argumentText = argument.getText();
          if (result.length() != 0) {
            insertPlus(result);
            if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE ||
                (type instanceof PsiPrimitiveType && ParenthesesUtils.getPrecedence(argument) == ParenthesesUtils.ADDITIVE_PRECEDENCE)) {
              result.append('(').append(argumentText).append(')');
            }
            else {
              if (StringUtil.startsWithChar(argumentText, '+')) {
                result.append(' ');
              }
              result.append(argumentText);
            }
          }
          else {
            if (type instanceof PsiPrimitiveType) {
              if (argument instanceof PsiLiteralExpression) {
                final PsiLiteralExpression literalExpression = (PsiLiteralExpression)argument;
                if (PsiType.CHAR.equals(literalExpression.getType())) {
                  result.append('"');
                  final Character c = (Character)literalExpression.getValue();
                  if (c != null) {
                    result.append(StringUtil.escapeStringCharacters(c.toString()));
                  }
                  result.append('"');
                }
                else {
                  result.append('"').append(literalExpression.getValue()).append('"');
                }
              }
              else {
                result.append("String.valueOf(").append(argumentText).append(")");
              }
            }
            else {
              if (ParenthesesUtils.getPrecedence(argument) >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
                result.append('(').append(argumentText).append(')');
              }
              else {
                if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                  result.append("String.valueOf(").append(argumentText).append(")");
                }
                else {
                  result.append(argumentText);
                }
              }
            }
          }
        }
      }
      return result;
    }

    private static void insertPlus(@NonNls StringBuilder result) {
      if (result.charAt(result.length() - 1) == '\n') {
        int index = result.length() - 2;
        while (index > 0) {
          final char c = result.charAt(index);
          if (c == '/' && result.charAt(index - 1) == '/') { // special handling of end-of-line comment
            result.insert(index - 1, "+ ");
            return;
          }
          if (c == '\n') {
            break;
          }
          index--;
        }
        result.insert(result.length() - 1, '+');
      }
      else {
        result.append('+');
      }
    }

    private static class StringBuildingVisitor extends JavaRecursiveElementWalkingVisitor {

      private final PsiVariable myVariable;
      private final StringBuilder myBuilder;
      private final List<PsiMethodCallExpression> expressions = ContainerUtil.newArrayList();
      private boolean myProblem;

      private StringBuildingVisitor(@NotNull PsiVariable variable, StringBuilder builder) {
        myVariable = variable;
        myBuilder = builder;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (myProblem) {
          return;
        }
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          return;
        }
        final PsiElement target = expression.resolve();
        if (!myVariable.equals(target)) {
          return;
        }
        PsiMethodCallExpression methodCallExpression = null;
        PsiElement parent = expression.getParent();
        PsiElement grandParent = parent.getParent();
        while (parent instanceof PsiReferenceExpression && grandParent instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)grandParent;
          parent = methodCallExpression.getParent();
          grandParent = parent.getParent();
          if ("toString".equals(methodCallExpression.getMethodExpression().getReferenceName())) {
            break;
          }
        }
        if (buildStringExpression(methodCallExpression, myBuilder) == null) {
          myProblem = true;
        }
        myBuilder.append('\n');
        expressions.add(methodCallExpression);
      }

      public List<PsiMethodCallExpression> getExpressions() {
        return expressions;
      }

      private boolean hadProblem() {
        return myProblem;
      }
    }
  }
}
