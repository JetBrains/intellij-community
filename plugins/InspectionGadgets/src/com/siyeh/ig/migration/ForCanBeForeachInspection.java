/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ForCanBeForeachInspection extends ForCanBeForeachInspectionBase {

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ForCanBeForeachFix();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel =
      new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "for.can.be.foreach.option"), "REPORT_INDEXED_LOOP");
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "for.can.be.foreach.option2"), "ignoreUntypedCollections");
    return panel;
  }

  @Nullable
  static PsiType getContentType(PsiType type, String containerClassName) {
    PsiType parameterType = PsiUtil.substituteTypeParameter(type, containerClassName, 0, true);
    return GenericsUtil.getVariableTypeByExpressionType(parameterType);
  }

  private class ForCanBeForeachFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement forElement = descriptor.getPsiElement();
      final PsiElement parent = forElement.getParent();
      if (!(parent instanceof PsiForStatement)) {
        return;
      }
      final PsiForStatement forStatement = (PsiForStatement)parent;
      final String newExpression;
      if (isArrayLoopStatement(forStatement)) {
        newExpression = createArrayIterationText(forStatement);
      }
      else if (isCollectionLoopStatement(forStatement, ignoreUntypedCollections)) {
        newExpression = createCollectionIterationText(forStatement);
      }
      else if (isIndexedListLoopStatement(forStatement, ignoreUntypedCollections)) {
        newExpression = createListIterationText(forStatement);
      }
      else {
        return;
      }
      if (newExpression == null) {
        return;
      }
      PsiReplacementUtil.replaceStatementAndShortenClassNames(forStatement, newExpression);
    }

    @Nullable
    private String createListIterationText(@NotNull PsiForStatement forStatement) {
      final PsiBinaryExpression condition =
        (PsiBinaryExpression)ParenthesesUtils.stripParentheses(
          forStatement.getCondition());
      if (condition == null) {
        return null;
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(condition.getLOperand());
      if (lhs == null) {
        return null;
      }
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(condition.getROperand());
      if (rhs == null) {
        return null;
      }
      final IElementType tokenType = condition.getOperationTokenType();
      final String indexName;
      PsiExpression collectionSize;
      if (JavaTokenType.LT.equals(tokenType)) {
        indexName = lhs.getText();
        collectionSize = rhs;
      }
      else if (JavaTokenType.GT.equals(tokenType)) {
        indexName = rhs.getText();
        collectionSize = lhs;
      }
      else {
        return null;
      }
      if (collectionSize instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collectionSize;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiVariable) {
          final PsiVariable variable = (PsiVariable)target;
          collectionSize = ParenthesesUtils.stripParentheses(variable.getInitializer());
        }
      }
      if (!(collectionSize instanceof PsiMethodCallExpression)) {
        return null;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)ParenthesesUtils.stripParentheses(collectionSize);
      if (methodCallExpression == null) {
        return null;
      }
      final PsiReferenceExpression listLengthExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(ExpressionUtils.getQualifierOrThis(listLengthExpression));
      final PsiReferenceExpression listReference;
      if (qualifier instanceof PsiReferenceExpression) {
        listReference = (PsiReferenceExpression)qualifier;
      }
      else {
        listReference = null;
      }
      PsiType parameterType;
      final PsiType type = qualifier.getType();
      if (type == null) {
        return null;
      }
      parameterType = getContentType(type, CommonClassNames.JAVA_UTIL_COLLECTION);
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(forStatement);
      }
      final String typeString = parameterType.getCanonicalText();
      final PsiVariable listVariable;
      if (listReference == null) {
        listVariable = null;
      }
      else {
        final PsiElement target = listReference.resolve();
        if (!(target instanceof PsiVariable)) {
          return null;
        }
        listVariable = (PsiVariable)target;
      }
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isListElementDeclaration(firstStatement, listVariable, indexName, parameterType);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        contentVariableName = variable.getName();
        statementToSkip = declarationStatement;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        final String collectionName;
        if (listReference == null) {
          collectionName = null;
        }
        else {
          collectionName = listReference.getReferenceName();
        }
        contentVariableName = createNewVariableName(forStatement, parameterType, collectionName);
        finalString = "";
        statementToSkip = null;
      }
      @NonNls final StringBuilder out = new StringBuilder("for(");
      out.append(finalString).append(typeString).append(' ').append(contentVariableName).append(": ");
      @NonNls final String listName;
      if (listReference == null) {
        listName = qualifier.getText();
      }
      else {
        listName = getVariableReferenceText(listReference, listVariable, forStatement);
      }
      out.append(listName).append(')');
      if (body != null) {
        replaceCollectionGetAccess(body, contentVariableName, listVariable, indexName, statementToSkip, out);
      }
      return out.toString();
    }

    @Nullable
    private String createCollectionIterationText(@NotNull PsiForStatement forStatement) {
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final PsiStatement initialization =
        forStatement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      final PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      final PsiElement declaredIterator =
        declaration.getDeclaredElements()[0];
      if (!(declaredIterator instanceof PsiVariable)) {
        return null;
      }
      final PsiVariable iteratorVariable = (PsiVariable)declaredIterator;
      final PsiMethodCallExpression initializer =
        (PsiMethodCallExpression)iteratorVariable.getInitializer();
      if (initializer == null) {
        return null;
      }
      final PsiType iteratorType = initializer.getType();
      if (iteratorType == null) {
        return null;
      }
      final PsiType iteratorContentType = getContentType(iteratorType, CommonClassNames.JAVA_UTIL_ITERATOR);
      final PsiType iteratorVariableType = iteratorVariable.getType();
      final PsiType contentType;
      final PsiClassType javaLangObject = TypeUtils.getObjectType(forStatement);
      if (iteratorContentType == null) {
        final PsiType iteratorVariableContentType =
          getContentType(iteratorVariableType, CommonClassNames.JAVA_UTIL_ITERATOR);
        if (iteratorVariableContentType == null) {
          contentType = javaLangObject;
        }
        else {
          contentType = iteratorVariableContentType;
        }
      }
      else {
        contentType = iteratorContentType;
      }
      final PsiReferenceExpression methodExpression =
        initializer.getMethodExpression();
      final PsiExpression collection = ExpressionUtils.getQualifierOrThis(methodExpression);
      final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iteratorVariable, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String finalString;
      final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement =
          (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements =
          declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        contentVariableName = variable.getName();
        statementToSkip = declarationStatement;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression)collection;
          final String collectionName =
            referenceExpression.getReferenceName();
          contentVariableName = createNewVariableName(forStatement,
                                                      contentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(forStatement,
                                                      contentType, null);
        }
        final Project project = forStatement.getProject();
        final CodeStyleSettings codeStyleSettings =
          CodeStyleSettingsManager.getSettings(project);
        if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      final String contentTypeString = contentType.getCanonicalText();
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(contentTypeString);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      if (!contentType.equals(javaLangObject) && iteratorContentType == null) {
        out.append('(').append("java.lang.Iterable<").append(contentTypeString).append('>').append(')');
      }
      out.append(collection.getText());
      out.append(')');
      replaceIteratorNext(body, contentVariableName, iteratorVariable, contentType, statementToSkip, out);
      return out.toString();
    }

    @Nullable
    private String createArrayIterationText(@NotNull PsiForStatement forStatement) {
      final PsiExpression condition = forStatement.getCondition();
      final PsiBinaryExpression strippedCondition = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(condition);
      if (strippedCondition == null) {
        return null;
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(strippedCondition.getLOperand());
      if (lhs == null) {
        return null;
      }
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(strippedCondition.getROperand());
      if (rhs == null) {
        return null;
      }
      final IElementType tokenType = strippedCondition.getOperationTokenType();
      final PsiReferenceExpression arrayLengthExpression;
      final String indexName;
      if (tokenType.equals(JavaTokenType.LT)) {
        arrayLengthExpression = (PsiReferenceExpression)ParenthesesUtils.stripParentheses(rhs);
        indexName = lhs.getText();
      }
      else if (tokenType.equals(JavaTokenType.GT)) {
        arrayLengthExpression = (PsiReferenceExpression)ParenthesesUtils.stripParentheses(lhs);
        indexName = rhs.getText();
      }
      else {
        return null;
      }
      if (arrayLengthExpression == null) {
        return null;
      }
      PsiReferenceExpression arrayReference = (PsiReferenceExpression)arrayLengthExpression.getQualifierExpression();
      if (arrayReference == null) {
        final PsiElement target = arrayLengthExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)target;
        final PsiExpression initializer = variable.getInitializer();
        if (!(initializer instanceof PsiReferenceExpression)) {
          return null;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
        arrayReference = (PsiReferenceExpression)referenceExpression.getQualifierExpression();
        if (arrayReference == null) {
          return null;
        }
      }
      final PsiType type = arrayReference.getType();
      if (!(type instanceof PsiArrayType)) {
        return null;
      }
      final PsiArrayType arrayType = (PsiArrayType)type;
      final PsiType componentType = arrayType.getComponentType();
      final String typeText = componentType.getCanonicalText();
      final PsiElement target = arrayReference.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      final PsiVariable arrayVariable = (PsiVariable)target;
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isArrayElementDeclaration(firstStatement, arrayVariable, indexName);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
          final String collectionName = arrayReference.getReferenceName();
          contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
          final Project project = forStatement.getProject();
          final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
          if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
          statementToSkip = null;
        }
        else {
          contentVariableName = variable.getName();
          statementToSkip = declarationStatement;
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
        }
      }
      else {
        final String collectionName = arrayReference.getReferenceName();
        contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
        final Project project = forStatement.getProject();
        final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
        if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(typeText);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      final String arrayName = getVariableReferenceText(arrayReference, arrayVariable, forStatement);
      out.append(arrayName);
      out.append(')');
      if (body != null) {
        replaceArrayAccess(body, contentVariableName, arrayVariable, indexName, statementToSkip, out);
      }
      return out.toString();
    }

    private void replaceArrayAccess(
      PsiElement element, String contentVariableName,
      PsiVariable arrayVariable, String indexName,
      PsiElement childToSkip, StringBuilder out) {
      if (isArrayLookup(element, indexName, arrayVariable)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          final String text = element.getText();
          if (PsiKeyword.INSTANCEOF.equals(text) &&
              out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(text);
        }
        else {
          boolean skippingWhiteSpace = false;
          for (final PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace &&
                     skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceArrayAccess(child, contentVariableName,
                                 arrayVariable, indexName,
                                 childToSkip, out);
            }
          }
        }
      }
    }

    private void replaceCollectionGetAccess(
      PsiElement element, String contentVariableName,
      PsiVariable listVariable, String indexName,
      PsiElement childToSkip, StringBuilder out) {
      if (isListGetLookup(element, indexName, listVariable)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          final String text = element.getText();
          if (PsiKeyword.INSTANCEOF.equals(text) &&
              out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(text);
        }
        else {
          boolean skippingWhiteSpace = false;
          for (final PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace &&
                     skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceCollectionGetAccess(child,
                                         contentVariableName,
                                         listVariable, indexName,
                                         childToSkip, out);
            }
          }
        }
      }
    }

    private boolean isListGetLookup(PsiElement element,
                                    String indexName,
                                    PsiVariable listVariable) {
      if (!(element instanceof PsiExpression)) {
        return false;
      }
      final PsiExpression expression = (PsiExpression)element;
      if (!expressionIsListGetLookup(expression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)
          ParenthesesUtils.stripParentheses(expression);
      if (methodCallExpression == null) {
        return false;
      }
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();

      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      if (!indexName.equals(expressions[0].getText())) {
        return false;
      }
      if (qualifierExpression == null ||
          qualifierExpression instanceof PsiThisExpression ||
          qualifierExpression instanceof PsiSuperExpression) {
        return listVariable == null;
      }
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifierExpression;
      final PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      return listVariable.equals(target);
    }

    private boolean isArrayElementDeclaration(
      PsiStatement statement, PsiVariable arrayVariable,
      String indexName) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      final PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)declaredElement;
      final PsiExpression initializer = variable.getInitializer();
      return isArrayLookup(initializer, indexName, arrayVariable);
    }

    private boolean isListElementDeclaration(
      PsiStatement statement, PsiVariable listVariable,
      String indexName, PsiType type) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      final PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)declaredElement;
      final PsiExpression initializer = variable.getInitializer();
      if (!isListGetLookup(initializer, indexName, listVariable)) {
        return false;
      }
      return type != null && type.equals(variable.getType());
    }

    private boolean isArrayLookup(
      PsiElement element, String indexName, PsiVariable arrayVariable) {
      if (element == null) {
        return false;
      }
      if (!(element instanceof PsiArrayAccessExpression)) {
        return false;
      }
      final PsiArrayAccessExpression arrayAccess =
        (PsiArrayAccessExpression)element;
      final PsiExpression indexExpression =
        arrayAccess.getIndexExpression();
      if (indexExpression == null) {
        return false;
      }
      if (!indexName.equals(indexExpression.getText())) {
        return false;
      }
      final PsiExpression arrayExpression =
        arrayAccess.getArrayExpression();
      if (!(arrayExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)arrayExpression;
      final PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      return arrayVariable.equals(target);
    }
  }

  static void replaceIteratorNext(PsiElement element, String contentVariableName,
                                  PsiVariable iterator, PsiType contentType, PsiElement childToSkip,
                                  StringBuilder out) {
    if (isIteratorNext(element, iterator, contentType)) {
      out.append(contentVariableName);
    }
    else {
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        final String text = element.getText();
        if (PsiKeyword.INSTANCEOF.equals(text) && out.charAt(out.length() - 1) != ' ') {
          out.append(' ');
        }
        out.append(text);
      }
      else {
        boolean skippingWhiteSpace = false;
        for (final PsiElement child : children) {
          if (shouldSkip(iterator, contentType, child) || child.equals(childToSkip)) {
            skippingWhiteSpace = true;
          }
          else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
            skippingWhiteSpace = false;
            replaceIteratorNext(child, contentVariableName, iterator, contentType, childToSkip, out);
          }
        }
      }
    }
  }

  static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
    if (!(child instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)child;
    final PsiExpression expression = expressionStatement.getExpression();
    return isIteratorNext(expression, iterator, contentType);
  }

  static boolean isIteratorNextDeclaration(
    PsiStatement statement, PsiVariable iterator,
    PsiType contentType) {
    if (!(statement instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declarationStatement =
      (PsiDeclarationStatement)statement;
    final PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)declaredElement;
    final PsiExpression initializer = variable.getInitializer();
    return isIteratorNext(initializer, iterator, contentType);
  }

  static  boolean isIteratorNext(PsiElement element, PsiVariable iterator, PsiType contentType) {
    if (element == null) {
      return false;
    }
    if (element instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression castExpression =
        (PsiTypeCastExpression)element;
      final PsiType type = castExpression.getType();
      if (type == null) {
        return false;
      }
      if (!type.equals(contentType)) {
        return false;
      }
      final PsiExpression operand =
        castExpression.getOperand();
      return isIteratorNext(operand, iterator, contentType);
    }
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression callExpression =
      (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList =
      callExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 0) {
      return false;
    }
    final PsiReferenceExpression reference =
      callExpression.getMethodExpression();
    @NonNls final String referenceName = reference.getReferenceName();
    if (!HardcodedMethodConstants.NEXT.equals(referenceName)) {
      return false;
    }
    final PsiExpression expression = reference.getQualifierExpression();
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    return iterator.equals(target);
  }

  static  String createNewVariableName(@NotNull PsiElement scope, PsiType type, @Nullable String containerName) {
    final Project project = scope.getProject();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    @NonNls String baseName;
    if (containerName != null) {
      baseName = StringUtils.createSingularFromName(containerName);
    }
    else {
      final SuggestedNameInfo suggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
      final String[] names = suggestions.names;
      if (names != null && names.length > 0) {
        baseName = names[0];
      }
      else {
        baseName = "value";
      }
    }
    if (baseName == null || baseName.isEmpty()) {
      baseName = "value";
    }
    return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
  }

  @Nullable
  static PsiStatement getFirstStatement(PsiStatement body) {
    if (!(body instanceof PsiBlockStatement)) {
      return body;
    }
    final PsiBlockStatement block = (PsiBlockStatement)body;
    final PsiCodeBlock codeBlock = block.getCodeBlock();
    return ControlFlowUtils.getFirstStatementInBlock(codeBlock);
  }

  @NotNull
  private static String getVariableReferenceText(PsiReferenceExpression reference, PsiVariable variable, PsiElement context) {
    final String text = reference.getText();
    final PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(context.getProject());
    PsiExpression qualifier = reference.getQualifierExpression();
    while(qualifier != null) {
      if(!(qualifier instanceof PsiReferenceExpression)) return text;
      qualifier = ((PsiReferenceExpression)qualifier).getQualifierExpression();
    }
    final PsiVariable target = resolveHelper.resolveReferencedVariable(text, context);
    return variable != target ? ExpressionUtils.getQualifierOrThis(reference).getText() + "." + text : text;
  }
}
