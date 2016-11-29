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

import java.util.ArrayList;
import java.util.Iterator;
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
    private final List<PsiComment> leadingComments = new ArrayList<>();
    private final List<PsiElement> commentsAndWhitespace = new ArrayList<>();

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
          collectComments(stringBuilderExpression);
          final StringBuilder stringExpression = buildStringExpression(stringBuilderExpression, new StringBuilder());
          if (stringExpression != null && stringBuilderExpression != null) {
            addLeadingCommentsBefore(stringBuilderExpression);
            addTrailingCommentsAfter(stringBuilderExpression);
            PsiReplacementUtil.replaceExpression(stringBuilderExpression, stringExpression.toString());
          }
        }
        return;
      }
      final PsiVariable variable = (PsiVariable)parent;
      final String variableName = variable.getName();
      if (variableName == null) {
        return;
      }
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
        collectComments(parent);
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
      final String expressionText = builder.toString().trim();
      final PsiMethodCallExpression lastExpression = expressions.get(expressions.size() - 1);
      final boolean useVariable = expressionText.contains("\n") && !isVariableInitializer(lastExpression);
      if (useVariable) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
        if (statement == null) {
          return;
        }
        final String modifier = CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS ? "final " : "";
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final StringBuilder statementText =
          new StringBuilder(modifier).append(CommonClassNames.JAVA_LANG_STRING).append(' ').append(variableName).append("=");
        for (PsiComment comment : leadingComments) {
          statementText.append(comment.getText());
          final PsiElement sibling = comment.getNextSibling();
          if (sibling instanceof PsiWhiteSpace) {
            statementText.append(sibling.getText());
          }
        }
        statementText.append(expressionText).append(';');
        final PsiStatement newStatement = factory.createStatementFromText(statementText.toString(), variable);
        codeBlock.addBefore(newStatement, statement);
        addTrailingCommentsAfter(lastExpression);
        PsiReplacementUtil.replaceExpression(lastExpression, variableName);
      }
      else {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(lastExpression, PsiStatement.class);
        if (statement == null) {
          return;
        }
        addLeadingCommentsBefore(statement);
        addTrailingCommentsAfter(statement);
        PsiReplacementUtil.replaceExpression(lastExpression, expressionText);
      }
      variable.delete();
      for (int i = 0, size = expressions.size() - 1; i < size; i++) {
        expressions.get(i).getParent().delete();
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
    private StringBuilder buildStringExpression(PsiElement element, @NonNls StringBuilder result) {
      if (element instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)element;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          return null;
        }
        addCommentsBefore(argumentList, result);
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

      if (element instanceof PsiMethodCallExpression) {
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
            addCommentsBefore(argumentList, result);
            result.append("String.valueOf").append(argumentList.getText());
            return result;
          }
          final PsiExpression argument = arguments[0];
          final PsiType type = argument.getType();
          final String argumentText = argument.getText();
          if (result.length() != 0) {
            insertPlus(result);
            addCommentsBefore(argument, result);
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
            addCommentsBefore(argumentList, result);
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

    private void addCommentsBefore(PsiElement anchor, StringBuilder out) {
      final int offset = anchor.getTextOffset();
      boolean newlineAdded = false;
      boolean newlineEncountered = false;
      for (final Iterator<PsiElement> iterator = commentsAndWhitespace.iterator(); iterator.hasNext(); ) {
        final PsiElement element = iterator.next();
        if (element.getTextOffset() >= offset) {
          break;
        }
        if (element instanceof PsiComment) {
          final PsiComment comment = (PsiComment)element;
          if (out.length() == 0) {
            leadingComments.add(comment);
          }
          else {
            final PsiElement prev = comment.getPrevSibling();
            if (prev instanceof PsiWhiteSpace) {
              out.append(prev.getText());
            }
            out.append(comment.getText());
            final PsiElement next = comment.getNextSibling();
            if (next instanceof PsiWhiteSpace) {
              final String text = next.getText();
              if (text.contains("\n")) {
                newlineAdded = true;
              }
              out.append(text);
            }
          }
        }
        else if (element instanceof PsiWhiteSpace) {
          final String text = element.getText();
          if (text.contains("\n")) {
            newlineEncountered = true;
          }
        }
        iterator.remove();
      }
      if (newlineEncountered && !newlineAdded && out.length() != 0) {
        out.append("\n");
      }
    }

    private void addLeadingCommentsBefore(PsiElement anchor) {
      final PsiElement parent = anchor.getParent();
      for (PsiComment comment : leadingComments) {
        parent.addBefore(comment, anchor);
        final PsiElement sibling = comment.getNextSibling();
        if (sibling instanceof PsiWhiteSpace) {
          parent.addBefore(sibling, anchor);
        }
      }
      leadingComments.clear();
    }

    private void addTrailingCommentsAfter(PsiElement anchor) {
      final PsiElement parent = anchor.getParent();
      for (int i = commentsAndWhitespace.size() - 1; i >= 0; i--) {
        final PsiElement element = commentsAndWhitespace.get(i);
        if (element instanceof PsiComment) {
          final PsiComment comment = (PsiComment)element;
          parent.addAfter(comment, anchor);
          final PsiElement sibling = comment.getPrevSibling();
          if (sibling instanceof PsiWhiteSpace) {
            parent.addAfter(sibling, anchor);
          }
        }
      }
      commentsAndWhitespace.clear();
    }

    private void collectComments(PsiElement element) {
      commentsAndWhitespace.addAll(PsiTreeUtil.findChildrenOfAnyType(element, PsiComment.class, PsiWhiteSpace.class));
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionStatement) && !(parent instanceof PsiDeclarationStatement)) {
        return;
      }
      PsiComment comment = PsiTreeUtil.getNextSiblingOfType(element, PsiComment.class);
      while (comment != null) {
        commentsAndWhitespace.add(comment);
        comment = PsiTreeUtil.getNextSiblingOfType(comment, PsiComment.class);
      }
      final PsiElement sibling = parent.getNextSibling();
      if (sibling instanceof PsiWhiteSpace) {
        commentsAndWhitespace.add(sibling);
      }
    }

    private static void insertPlus(@NonNls StringBuilder result) {
      final int lastIndex = result.length() - 1;
      if (result.charAt(lastIndex) == '\n') {
        int index = result.length() - 2;
        while (index > 0) {
          if (result.charAt(index) == '\n') {
            break;
          }
          index--;
        }
        boolean insideLiteral = false;
        boolean escape = false;
        while (index < lastIndex) {
          final char c = result.charAt(index);
          if (c == '"' && !escape) {
            insideLiteral = !insideLiteral;
          }
          else if (c == '\\' && insideLiteral) {
            escape = true;
            index++;
            continue;
          }
          else if (!insideLiteral && c == '/' && result.charAt(index + 1) == '/') {
            if (index != 0) {
              result.insert(index, "+ ");
            }
            return;
          }
          index++;
          escape = false;
        }
        result.insert(lastIndex, '+');
      }
      else {
        result.append('+');
      }
    }

    private class StringBuildingVisitor extends JavaRecursiveElementWalkingVisitor {

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
        collectComments(methodCallExpression);
        if (buildStringExpression(methodCallExpression, myBuilder) == null) {
          myProblem = true;
        }
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
