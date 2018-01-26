/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceForEachLoopWithIndexedForLoopIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IndexedForEachLoopPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiForeachStatement statement = (PsiForeachStatement)element.getParent();
    if (statement == null) {
      return;
    }
    final PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue == null) {
      return;
    }
    final PsiParameter iterationParameter =
      statement.getIterationParameter();
    final PsiType type = iterationParameter.getType();
    final PsiType iteratedValueType = iteratedValue.getType();
    if (iteratedValueType == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final boolean isArray = iteratedValueType instanceof PsiArrayType;
    final PsiElement grandParent = statement.getParent();
    final PsiStatement context;
    if (grandParent instanceof PsiLabeledStatement) {
      context = (PsiStatement)grandParent;
    } else {
      context = statement;
    }
    final String iteratedValueText = getReferenceToIterate(iteratedValue, context, tracker);
    @NonNls final StringBuilder newStatement = new StringBuilder();
    final String indexText = createVariableName("i", PsiType.INT, statement);
    createForLoopDeclaration(statement, iteratedValue, isArray, iteratedValueText, newStatement, indexText);
    final Project project = statement.getProject();
    final CodeStyleSettings codeStyleSettings =
      CodeStyleSettingsManager.getSettings(project);
    if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
      newStatement.append("final ");
    }
    newStatement.append(type.getCanonicalText());
    newStatement.append(' ');
    newStatement.append(iterationParameter.getName());
    newStatement.append(" = ");
    newStatement.append(iteratedValueText);
    if (isArray) {
      newStatement.append('[');
      newStatement.append(indexText);
      newStatement.append("];");
    }
    else {
      newStatement.append(".get(");
      newStatement.append(indexText);
      newStatement.append(");");
    }
    final PsiStatement body = statement.getBody();
    if (body == null) {
      return;
    }
    if (body instanceof PsiBlockStatement) {
      final PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
      final PsiElement[] children = block.getChildren();
      for (int i = 1; i < children.length - 1; i++) {
        //skip the braces
        newStatement.append(tracker.text(children[i]));
      }
    }
    else {
      newStatement.append(tracker.text(body));
    }
    newStatement.append('}');
    PsiReplacementUtil.replaceStatementAndShortenClassNames(statement, newStatement.toString(), tracker);
  }

  protected void createForLoopDeclaration(PsiForeachStatement statement,
                                            PsiExpression iteratedValue,
                                            boolean array,
                                            String iteratedValueText, StringBuilder newStatement, 
                                            final String indexText) {
    newStatement.append("for(int ");
    newStatement.append(indexText);
    newStatement.append(" = 0; ");
    newStatement.append(indexText);
    newStatement.append('<');
    if (iteratedValue instanceof PsiTypeCastExpression) {
      newStatement.append('(');
      newStatement.append(iteratedValueText);
      newStatement.append(')');
    }
    else {
      newStatement.append(iteratedValueText);
    }
    if (array) {
      newStatement.append(".length");
    }
    else {
      newStatement.append(".size()");
    }
    newStatement.append(';');
    newStatement.append(indexText);
    newStatement.append("++)");
    newStatement.append("{ ");
  }

  @Nullable
  private static String getVariableName(PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (name == null) {
        return null;
      }
      if (name.startsWith("to") && name.length() > 2) {
        return StringUtil.decapitalize(name.substring(2));
      }
      else if (name.startsWith("get") && name.length() > 3) {
        return StringUtil.decapitalize(name.substring(3));
      }
      else {
        return name;
      }
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression castExpression =
        (PsiTypeCastExpression)expression;
      final PsiExpression operand = castExpression.getOperand();
      return getVariableName(operand);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression =
        (PsiArrayAccessExpression)expression;
      final PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      final String name = getVariableName(arrayExpression);
      return (name == null) ? null : StringUtil.unpluralize(name);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      final PsiExpression innerExpression =
        parenthesizedExpression.getExpression();
      return getVariableName(innerExpression);
    }
    else if (expression instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)expression;
      final String referenceName = referenceElement.getReferenceName();
      if (referenceName == null) {
        return expression.getText();
      }
      return referenceName;
    }
    return null;
  }

  private static String getReferenceToIterate(PsiExpression expression, PsiElement context, CommentTracker tracker) {
    if (expression instanceof PsiMethodCallExpression ||
        expression instanceof PsiTypeCastExpression ||
        expression instanceof PsiArrayAccessExpression ||
        expression instanceof PsiNewExpression) {
      final String variableName = getVariableName(expression);
      return createVariable(variableName, expression, context);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      final PsiExpression innerExpression =
        parenthesizedExpression.getExpression();
      return getReferenceToIterate(innerExpression, context, tracker);
    }
    else if (expression instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)expression;
      final String variableName = getVariableName(expression);
      if (referenceElement.isQualified()) {
        return createVariable(variableName, expression, context);
      }
      final PsiElement target = referenceElement.resolve();
      if (target instanceof PsiVariable) {
        // maybe should not do this for local variables outside of
        // anonymous classes
        return variableName;
      }
      return createVariable(variableName, expression, context);
    }
    return tracker.text(expression);
  }

  private static String createVariable(String variableNameRoot,
                                       PsiExpression iteratedValue,
                                       PsiElement context) {
    final String variableName =
      createVariableName(variableNameRoot, iteratedValue);
    final Project project = context.getProject();
    final PsiType iteratedValueType = iteratedValue.getType();
    assert iteratedValueType != null;
    final PsiElementFactory elementFactory =
      JavaPsiFacade.getElementFactory(project);
    final PsiDeclarationStatement declarationStatement =
      elementFactory.createVariableDeclarationStatement(variableName,
                                                        iteratedValueType, iteratedValue);
    final PsiElement newElement = context.getParent().addBefore(declarationStatement, context);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
    return variableName;
  }

  public static String createVariableName(
    @Nullable String baseName,
    @NotNull PsiExpression assignedExpression) {
    final Project project = assignedExpression.getProject();
    final JavaCodeStyleManager codeStyleManager =
      JavaCodeStyleManager.getInstance(project);
    final SuggestedNameInfo names =
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE,
                                           baseName, assignedExpression, null);
    if (names.names.length == 0) {
      return codeStyleManager.suggestUniqueVariableName(baseName,
                                                        assignedExpression, true);
    }
    return codeStyleManager.suggestUniqueVariableName(names.names[0],
                                                      assignedExpression, true);
  }

  public static String createVariableName(@Nullable String baseName,
                                          @NotNull PsiType type,
                                          @NotNull PsiElement context) {
    final Project project = context.getProject();
    final JavaCodeStyleManager codeStyleManager =
      JavaCodeStyleManager.getInstance(project);
    final SuggestedNameInfo names =
      codeStyleManager.suggestVariableName(
        VariableKind.LOCAL_VARIABLE, baseName, null, type);
    if (names.names.length == 0) {
      return codeStyleManager.suggestUniqueVariableName(baseName,
                                                        context, true);
    }
    return codeStyleManager.suggestUniqueVariableName(names.names[0],
                                                      context, true);
  }
}