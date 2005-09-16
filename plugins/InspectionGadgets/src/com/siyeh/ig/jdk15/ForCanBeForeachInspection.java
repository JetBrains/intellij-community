/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class ForCanBeForeachInspection extends StatementInspection {

  private final ForCanBeForeachFix fix = new ForCanBeForeachFix();

  public String getID() {
    return "ForLoopReplaceableByForEach";
  }

  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ForCanBeForeachVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ForCanBeForeachFix extends InspectionGadgetsFix {
    private static String indexName;

    public String getName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiElement forElement = descriptor.getPsiElement();
      if (!(forElement.getParent() instanceof PsiForStatement)) {
        return;
      }
      final PsiForStatement forStatement =
        (PsiForStatement)forElement.getParent();
      final String newExpression;
      if (isArrayLoopStatement(forStatement)) {
        newExpression = createArrayIterationText(forStatement, project);
      }
      else if (isCollectionLoopStatement(forStatement)) {
        newExpression = createCollectionIterationText(forStatement,
                                                      project);
      }
      else if (isIndexedListLoopStatement(forStatement)) {
        newExpression = createListIterationText(forStatement, project);
      }
      else {
        return;
      }
      replaceStatementAndShortenClassNames(forStatement, newExpression);
    }

    private String createListIterationText(
      final PsiForStatement forStatement, final Project project) {
      final int length = forStatement.getText().length();
      @NonNls final StringBuffer out = new StringBuffer(length);
      final PsiBinaryExpression condition =
        (PsiBinaryExpression)forStatement.getCondition();
      final PsiExpression lhs = condition.getLOperand();
      final String indexName = lhs.getText();
      PsiExpression rOperand = condition.getROperand();
      assert rOperand != null;
      final PsiReferenceExpression arrayLengthExpression =
        ((PsiMethodCallExpression)rOperand).getMethodExpression();
      assert arrayLengthExpression != null;
      final PsiReferenceExpression arrayReference =
        (PsiReferenceExpression)arrayLengthExpression
          .getQualifierExpression();
      final PsiClassType arrayType = (PsiClassType)arrayReference
        .getType();
      PsiType[] parameters = arrayType.getParameters();
      final PsiType componentType = parameters.length == 1 ? parameters[0] :
                                    PsiType.getJavaLangObject(arrayReference.getManager(),
                                                              GlobalSearchScope.allScope(
                                                                project));
      final String type = componentType == null ? "java.lang.Object" :
                          componentType.getPresentableText();
      final String arrayName = arrayReference.getText();
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isListElementDeclaration(
        firstStatement, arrayName, indexName, componentType);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement decl = (PsiDeclarationStatement)firstStatement;
        assert decl != null;
        final PsiElement[] declaredElements = decl
          .getDeclaredElements();
        final PsiLocalVariable localVar =
          (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVar.getName();
        statementToSkip = decl;
        if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        final String collectionName =
          arrayReference.getReferenceName();
        contentVariableName = createNewVarName(project,
                                               forStatement,
                                               componentType,
                                               collectionName,
                                               arrayReference.resolve());
        finalString = "";
        statementToSkip = null;
      }
      out.append("for(" + finalString + type + ' ' + contentVariableName +
                 ": " + arrayName + ')');
      replaceCollectionGetAccess(body, contentVariableName, arrayName,
                                 indexName,
                                 statementToSkip, out);
      return out.toString();
    }

    private String createCollectionIterationText(
      PsiForStatement forStatement,
      Project project)
      throws IncorrectOperationException {
      final int length = forStatement.getText().length();
      @NonNls final StringBuffer out = new StringBuffer(length);
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final PsiStatement initialization =
        forStatement.getInitialization();
      final PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      final PsiLocalVariable iterator =
        (PsiLocalVariable)declaration.getDeclaredElements()[0];

      final PsiClassType iteratorType = (PsiClassType)iterator.getType();
      final PsiType[] iteratorTypeParameters = iteratorType
        .getParameters();
      PsiType iteratedContentsType = null;
      if (iteratorTypeParameters.length == 1) {
        final PsiType parameterType = iteratorTypeParameters[0];
        if (parameterType instanceof PsiWildcardType) {
          final PsiWildcardType wildcardType = (PsiWildcardType)parameterType;
          iteratedContentsType = wildcardType.getExtendsBound();
        }
        else {
          iteratedContentsType = parameterType;
        }
      }
      final PsiMethodCallExpression initializer =
        (PsiMethodCallExpression)iterator.getInitializer();
      final PsiExpression collection =
        initializer.getMethodExpression().getQualifierExpression();
      final PsiClassType collectionType = (PsiClassType)collection
        .getType();

      final PsiType[] parameters = collectionType.getParameters();
      final String collectionContentsTypeString;
      if (parameters.length == 1) {
        final PsiType parameterType = parameters[0];
        if (parameterType instanceof PsiWildcardType) {
          final PsiWildcardType wildcardType = (PsiWildcardType)parameterType;
          final PsiType bound = wildcardType.getExtendsBound();
          collectionContentsTypeString = bound.getCanonicalText();
        }
        else if (parameterType != null) {
          collectionContentsTypeString = parameterType
            .getCanonicalText();
        }
        else {
          collectionContentsTypeString = "java.lang.Object";
        }
      }
      else {
        collectionContentsTypeString = "java.lang.Object";
      }
      final String contentTypeString;

      if (iteratedContentsType != null) {
        contentTypeString = iteratedContentsType.getCanonicalText();
      }
      else {
        contentTypeString = collectionContentsTypeString;
      }
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiElementFactory elementFactory =
        psiManager.getElementFactory();
      final PsiType contentType =
        elementFactory.createTypeFromText(contentTypeString,
                                          forStatement);
      final String iteratorName = iterator.getName();
      final boolean isDeclaration =
        isIteratorNextDeclaration(firstStatement, iteratorName,
                                  contentTypeString);
      final PsiStatement statementToSkip;
      @NonNls final String finalString;
      final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement decl =
          (PsiDeclarationStatement)firstStatement;
        assert decl != null;
        final PsiElement[] declaredElements =
          decl.getDeclaredElements();
        final PsiLocalVariable localVar =
          (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVar.getName();
        statementToSkip = decl;
        if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final String collectionName =
            ((PsiReferenceExpression)collection)
              .getReferenceName();
          contentVariableName = createNewVarName(project,
                                                 forStatement,
                                                 contentType,
                                                 collectionName, ((PsiReferenceExpression) collection).resolve());
        }
        else {
          contentVariableName = createNewVarName(project,
                                                 forStatement,
                                                 contentType, null, null);
        }
        if (CodeStyleSettingsManager
          .getSettings(project).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }

      @NonNls final String iterableTypeString = "java.lang.Iterable<"
                                        + contentTypeString + '>';
      final PsiManager manager = forStatement.getManager();
      final PsiElementFactory factory = manager.getElementFactory();
      final PsiType collectionContentsType = factory
        .createTypeFromText(collectionContentsTypeString,
                            forStatement);
      final String castString;

      if (iteratedContentsType == null ||
          iteratedContentsType
            .isAssignableFrom(collectionContentsType)) {
        castString = "";
      }
      else {
        castString = "(" + iterableTypeString + ")";
      }
      out.append("for(" + finalString + contentTypeString + ' ' +
                 contentVariableName + ": " + castString
                 + collection.getText() + ')');
      replaceIteratorNext(body, contentVariableName, iteratorName,
                          statementToSkip, out, contentTypeString);
      return out.toString();
    }

    private String createArrayIterationText(PsiForStatement forStatement,
                                            Project project) {
      final int length = forStatement.getText().length();
      @NonNls final StringBuffer out = new StringBuffer(length);
      final PsiBinaryExpression condition =
        (PsiBinaryExpression)forStatement.getCondition();
      final PsiExpression lhs = condition.getLOperand();
      final String indexName = lhs.getText();
      final PsiReferenceExpression arrayLengthExpression =
        (PsiReferenceExpression)condition.getROperand();
      assert arrayLengthExpression != null;
      final PsiReferenceExpression arrayReference =
        (PsiReferenceExpression)arrayLengthExpression
          .getQualifierExpression();
      final PsiArrayType arrayType =
        (PsiArrayType)arrayReference.getType();
      final PsiType componentType = arrayType.getComponentType();
      final String type = componentType.getPresentableText();
      final String arrayName = arrayReference.getText();
      final PsiStatement body = forStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration =
        isArrayElementDeclaration(firstStatement, arrayName,
                                  indexName);
      final String contentVariableName;
      @NonNls final String finalString;
      final PsiStatement statementToSkip;
      if (isDeclaration) {
        final PsiDeclarationStatement decl =
          (PsiDeclarationStatement)firstStatement;
        assert decl != null;
        final PsiElement[] declaredElements =
          decl.getDeclaredElements();
        final PsiLocalVariable localVar =
          (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVar.getName();
        statementToSkip = decl;
        if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        final String collectionName =
          arrayReference.getReferenceName();
        contentVariableName = createNewVarName(project,
                                               forStatement,
                                               componentType,
                                               collectionName,
                                               arrayReference.resolve());
        finalString = "";
        statementToSkip = null;
      }
      out.append("for(" + finalString + type + ' ' + contentVariableName +
                 ": " + arrayName + ')');
      replaceArrayAccess(body, contentVariableName, arrayName, indexName,
                         statementToSkip, out);
      return out.toString();
    }

    private void replaceArrayAccess(PsiElement element,
                                    String contentVariableName,
                                    String arrayName, String indexName,
                                    PsiElement childToSkip,
                                    StringBuffer out) {
      if (isArrayLookup(element, indexName, arrayName)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(element.getText());
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
                                 arrayName, indexName,
                                 childToSkip, out);
            }
          }
        }
      }
    }

    private void replaceCollectionGetAccess(PsiElement element,
                                            String contentVariableName,
                                            String arrayName,
                                            String indexName,
                                            PsiElement childToSkip,
                                            StringBuffer out) {
      if (isListGetLookup(element, indexName, arrayName)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(element.getText());
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
                                         arrayName, indexName,
                                         childToSkip, out);
            }
          }
        }
      }
    }

    private static boolean isListGetLookup(PsiElement element,
                                           String indexName,
                                           String arrayName) {
      ForCanBeForeachFix.indexName = indexName;
      if (!(element instanceof PsiExpression && expressionIsListGetLookup(
        (PsiExpression)element))) {
        return false;
      }

      final PsiExpression expression = (PsiExpression)element;
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)ParenthesesUtils.stripParentheses(expression);
      if (!arrayName
        .equals(methodCallExpression.getMethodExpression()
          .getQualifierExpression().getText())) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression
        .getArgumentList();
      if (argumentList == null) {
        return false;
      }
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      return indexName.equals(expressions[0].getText());
    }

    private void replaceIteratorNext(PsiElement element,
                                     String contentVariableName,
                                     String iteratorName,
                                     PsiElement childToSkip,
                                     StringBuffer out,
                                     String contentType) {

      if (isIteratorNext(element, iteratorName, contentType)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(element.getText());
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
              replaceIteratorNext(child, contentVariableName,
                                  iteratorName,
                                  childToSkip, out, contentType);
            }
          }
        }
      }
    }

    private static boolean isArrayElementDeclaration(PsiStatement statement,
                                                     String arrayName,
                                                     String indexName) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement decl =
        (PsiDeclarationStatement)statement;
      final PsiElement[] elements = decl.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      if (!(elements[0] instanceof PsiLocalVariable)) {
        return false;
      }
      final PsiLocalVariable var = (PsiLocalVariable)elements[0];
      final PsiExpression initializer = var.getInitializer();
      return isArrayLookup(initializer, indexName, arrayName);
    }

    private static boolean isListElementDeclaration(PsiStatement statement,
                                                    String arrayName,
                                                    String indexName,
                                                    PsiType type) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement decl =
        (PsiDeclarationStatement)statement;
      final PsiElement[] elements = decl.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      if (!(elements[0] instanceof PsiLocalVariable)) {
        return false;
      }
      final PsiLocalVariable var = (PsiLocalVariable)elements[0];
      final PsiExpression initializer = var.getInitializer();
      if (!isListGetLookup(initializer, indexName,
                           arrayName)) {
        return false;
      }
      return type != null && type.equals(var.getType());
    }

    private static boolean isIteratorNextDeclaration(PsiStatement statement,
                                                     String iteratorName,
                                                     String contentType) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement decl =
        (PsiDeclarationStatement)statement;
      final PsiElement[] elements = decl.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      if (!(elements[0] instanceof PsiLocalVariable)) {
        return false;
      }
      final PsiLocalVariable var = (PsiLocalVariable)elements[0];
      final PsiExpression initializer = var.getInitializer();
      return isIteratorNext(initializer, iteratorName, contentType);
    }

    private static boolean isArrayLookup(PsiElement element,
                                         String indexName,
                                         String arrayName) {
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
      return arrayName.equals(arrayExpression.getText());
    }

    private static boolean isIteratorNext(PsiElement element,
                                          String iteratorName,
                                          String contentType) {
      if (element == null) {
        return false;
      }
      if (element instanceof PsiTypeCastExpression) {

        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
        final PsiType type = castExpression.getType();
        if (type == null) {
          return false;
        }
        if (!type.getPresentableText().equals(contentType)) {
          return false;
        }
        final PsiExpression operand =
          castExpression.getOperand();
        return isIteratorNext(operand, iteratorName, contentType);
      }
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args == null || args.length != 0) {
        return false;
      }
      final PsiReferenceExpression reference =
        callExpression.getMethodExpression();
      if (reference == null) {
        return false;
      }
      final PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      if (!iteratorName.equals(qualifier.getText())) {
        return false;
      }
      @NonNls final String referenceName = reference.getReferenceName();
      return "next".equals(referenceName);
    }

    private static String createNewVarName(Project project,
                                           PsiForStatement scope,
                                           PsiType type,
                                           String containerName,
                                           PsiElement collectionVariable) {
      final CodeStyleManager codeStyleManager =
        CodeStyleManager.getInstance(project);
      @NonNls String baseName;
      if (collectionVariable instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable) collectionVariable;
        String variableName = variable.getName();
        String propertyName = codeStyleManager.variableNameToPropertyName(variableName, codeStyleManager.getVariableKind(variable));
        propertyName = StringUtils.createSingularFromName(propertyName);
        baseName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      } else if (containerName != null) {
        baseName = StringUtils.createSingularFromName(containerName);
      }
      else {

        final SuggestedNameInfo suggestions =
          codeStyleManager
            .suggestVariableName(
              VariableKind.LOCAL_VARIABLE,
              null, null, type);
        final String[] names = suggestions.names;
        if (names != null && names.length > 0) {
          baseName = names[0];
        }
        else {
          baseName = "value";
        }
      }

      if (baseName == null || baseName.length() == 0) {
        baseName = "value";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope,
                                                        true);
    }

    @Nullable
    private static PsiStatement getFirstStatement(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length > 0) {
          return statements[0];
        }
        else {
          return null;
        }
      }
      else {
        return body;
      }
    }
  }

  private static class ForCanBeForeachVisitor
    extends StatementInspectionVisitor {
    public void visitForStatement(@NotNull PsiForStatement forStatement) {
      super.visitForStatement(forStatement);
      final PsiManager manager = forStatement.getManager();
      final LanguageLevel languageLevel =
        manager.getEffectiveLanguageLevel();
      if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
          languageLevel.equals(LanguageLevel.JDK_1_4)) {
        return;
      }
      if (isArrayLoopStatement(forStatement)
          || isCollectionLoopStatement(forStatement)
          || isIndexedListLoopStatement(forStatement)) {
        registerStatementError(forStatement);
      }
    }
  }

  private static boolean isIndexedListLoopStatement(
    final PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
    if (declaration.getDeclaredElements().length != 1) {
      return false;
    }
    final PsiLocalVariable indexVar = (PsiLocalVariable)declaration
      .getDeclaredElements()[0];
    final PsiExpression initialValue = indexVar.getInitializer();
    if (initialValue == null) {
      return false;
    }
    final String initializerText = initialValue.getText();
    if (!"0".equals(initializerText)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!isListSizeComparison(condition, indexVar)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!isIncrement(update, indexVar)) {
      return false;
    }
    final PsiReferenceExpression collectionReference = getVariableReferenceFromCondition(
      condition);
    final PsiStatement body = forStatement.getBody();
    if (body == null) {
      return false;
    }
    PsiElement resolved = collectionReference.resolve();
    if (!(resolved instanceof PsiVariable)) {
      return false;
    }
    if (!indexVarOnlyUsedAsListIndex((PsiVariable)resolved, indexVar,
                                     body)) {
      return false;
    }

    final String collectionName = collectionReference.getText();
    return !isVariableAssigned(collectionName, body);
  }

  private static boolean isArrayLoopStatement(PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declaration =
      (PsiDeclarationStatement)initialization;
    if (declaration.getDeclaredElements().length != 1) {
      return false;
    }
    final PsiLocalVariable indexVar =
      (PsiLocalVariable)declaration.getDeclaredElements()[0];
    final PsiExpression initialValue = indexVar.getInitializer();
    if (initialValue == null) {
      return false;
    }
    final String initializerText = initialValue.getText();
    if (!"0".equals(initializerText)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!isArrayLengthComparison(condition, indexVar)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!isIncrement(update, indexVar)) {
      return false;
    }
    final PsiReferenceExpression arrayReference =
      getVariableReferenceFromCondition(condition);
    final PsiStatement body = forStatement.getBody();
    if (body == null) {
      return false;
    }
    final String arrayName = arrayReference.getText();
    if (!indexVarOnlyUsedAsIndex(arrayName, indexVar, body)) {
      return false;
    }
    return !isVariableAssigned(arrayName, body);
  }

  private static boolean isVariableAssigned(String arrayReference,
                                            PsiStatement body) {
    final VariableAssignmentVisitor visitor =
      new VariableAssignmentVisitor(arrayReference);
    body.accept(visitor);
    return visitor.isArrayAssigned();
  }

  private static boolean indexVarOnlyUsedAsIndex(String arrayName,
                                                 PsiLocalVariable indexVar,
                                                 PsiStatement body) {
    final IndexOnlyUsedAsIndexVisitor visitor =
      new IndexOnlyUsedAsIndexVisitor(arrayName, indexVar);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  private static boolean indexVarOnlyUsedAsListIndex(PsiVariable collection,
                                                     PsiLocalVariable indexVar,
                                                     PsiStatement body) {
    final VariableOnlyUsedAsListIndexVisitor visitor =
      new VariableOnlyUsedAsListIndexVisitor(collection, indexVar);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  private static boolean isCollectionLoopStatement(
    PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declaration =
      (PsiDeclarationStatement)initialization;
    if (declaration.getDeclaredElements().length != 1) {
      return false;
    }
    final PsiLocalVariable declaredVar =
      (PsiLocalVariable)declaration.getDeclaredElements()[0];
    if (declaredVar == null) {
      return false;
    }
    final PsiType declaredVarType = declaredVar.getType();
    if (!(declaredVarType instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)declaredVarType;
    final PsiClass declaredClass = classType.resolve();
    if (declaredClass == null) {
      return false;
    }
    if (!ClassUtils.isSubclass(declaredClass, "java.util.Iterator")) {
      return false;
    }
    final PsiExpression initialValue = declaredVar.getInitializer();
    if (initialValue == null) {
      return false;
    }
    if (!(initialValue instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression initialCall =
      (PsiMethodCallExpression)initialValue;
    final PsiReferenceExpression initialMethodExpression =
      initialCall.getMethodExpression();
    if (initialMethodExpression == null) {
      return false;
    }
    @NonNls final String initialCallName =
      initialMethodExpression.getReferenceName();
    if (!"iterator".equals(initialCallName)) {
      return false;
    }
    final PsiExpression qualifier = initialMethodExpression
      .getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    final PsiType qualifierType = qualifier.getType();
    if (!(qualifierType instanceof PsiClassType)) {
      return false;
    }

    final PsiClass qualifierClass =
      ((PsiClassType)qualifierType).resolve();
    if (qualifierClass == null) {
      return false;
    }
    if (!ClassUtils.isSubclass(qualifierClass, "java.lang.Iterable") &&
        !ClassUtils.isSubclass(qualifierClass,
                               "java.util.Collection")) {

      return false;
    }
    final String iteratorName = declaredVar.getName();
    final PsiExpression condition = forStatement.getCondition();
    if (!isHasNext(condition, iteratorName)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (update != null && !(update instanceof PsiEmptyStatement)) {
      return false;
    }
    final PsiStatement body = forStatement.getBody();
    if (body == null) {
      return false;
    }
    if (calculateCallsToIteratorNext(iteratorName, body) != 1) {
      return false;
    }
    if (isIteratorRemoveCalled(iteratorName, body)) {
      return false;
    }
    if (isIteratorHasNextCalled(iteratorName, body)) {
      return false;
    }
    return !isIteratorAssigned(iteratorName, body);
  }

  private static int calculateCallsToIteratorNext(String iteratorName,
                                                  PsiStatement body) {
    final NumCallsToIteratorNextVisitor visitor =
      new NumCallsToIteratorNextVisitor(iteratorName);
    body.accept(visitor);
    return visitor.getNumCallsToIteratorNext();
  }

  private static boolean isIteratorAssigned(String iteratorName,
                                            PsiStatement body) {
    final IteratorAssignmentVisitor visitor =
      new IteratorAssignmentVisitor(iteratorName);
    body.accept(visitor);
    return visitor.isIteratorAssigned();
  }

  private static boolean isIteratorRemoveCalled(String iteratorName,
                                                PsiStatement body) {
    final IteratorRemoveVisitor visitor =
      new IteratorRemoveVisitor(iteratorName);
    body.accept(visitor);
    return visitor.isRemoveCalled();
  }

  private static boolean isIteratorHasNextCalled(String iteratorName,
                                                 PsiStatement body) {
    final IteratorHasNextVisitor visitor =
      new IteratorHasNextVisitor(iteratorName);
    body.accept(visitor);
    return visitor.isHasNextCalled();
  }

  private static boolean isHasNext(PsiExpression condition, String iterator) {
    if (!(condition instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call =
      (PsiMethodCallExpression)condition;
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] args = argumentList.getExpressions();
    if (args.length != 0) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    if (methodExpression == null) {
      return false;
    }
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"hasNext".equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (qualifier == null) {
      return true;
    }
    final String target = qualifier.getText();
    return iterator.equals(target);
  }

  private static PsiReferenceExpression getVariableReferenceFromCondition(
    PsiExpression condition) {
    final PsiExpression strippedCondition =
      ParenthesesUtils.stripParentheses(condition);
    final PsiBinaryExpression binaryExp =
      (PsiBinaryExpression)strippedCondition;
    PsiExpression rhs = ParenthesesUtils
      .stripParentheses(binaryExp.getROperand());
    if (rhs instanceof PsiMethodCallExpression) {
      rhs = ((PsiMethodCallExpression)rhs).getMethodExpression();
    }
    return (PsiReferenceExpression)((PsiReferenceExpression)rhs)
      .getQualifierExpression();
  }

  private static boolean isIncrement(PsiStatement statement,
                                     PsiLocalVariable var) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    PsiExpression exp =
      ((PsiExpressionStatement)statement).getExpression();
    exp = ParenthesesUtils.stripParentheses(exp);
    if (exp instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExp = (PsiPrefixExpression)exp;
      final PsiJavaToken sign = prefixExp.getOperationSign();
      if (sign == null) {
        return false;
      }
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return false;
      }
      final PsiExpression operand = prefixExp.getOperand();
      return expressionIsVariableLookup(operand, var);
    }
    else if (exp instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExp = (PsiPostfixExpression)exp;
      final PsiJavaToken sign = postfixExp.getOperationSign();
      if (sign == null) {
        return false;
      }
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return false;
      }
      final PsiExpression operand = postfixExp.getOperand();
      return expressionIsVariableLookup(operand, var);
    }
    return false;
  }

  private static boolean isArrayLengthComparison(PsiExpression condition,
                                                 PsiLocalVariable var) {
    final PsiExpression strippedCondition =
      ParenthesesUtils.stripParentheses(condition);

    if (!(strippedCondition instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExp =
      (PsiBinaryExpression)strippedCondition;
    final PsiJavaToken sign = binaryExp.getOperationSign();
    if (!sign.getTokenType().equals(JavaTokenType.LT)) {
      return false;
    }
    final PsiExpression lhs = binaryExp.getLOperand();
    if (!expressionIsVariableLookup(lhs, var)) {
      return false;
    }
    final PsiExpression rhs = binaryExp.getROperand();
    return expressionIsArrayLengthLookup(rhs);
  }

  private static boolean isListSizeComparison(PsiExpression condition,
                                              PsiLocalVariable var) {
    final PsiExpression strippedCondition =
      ParenthesesUtils.stripParentheses(condition);

    if (!(strippedCondition instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExp =
      (PsiBinaryExpression)strippedCondition;
    final PsiJavaToken sign = binaryExp.getOperationSign();
    if (!sign.getTokenType().equals(JavaTokenType.LT)) {
      return false;
    }
    final PsiExpression lhs = binaryExp.getLOperand();
    if (!expressionIsVariableLookup(lhs, var)) {
      return false;
    }
    final PsiExpression rhs = binaryExp.getROperand();
    return expressionIsListSizeLookup(rhs);
  }

  private static boolean expressionIsListSizeLookup(PsiExpression expression) {
    return isListMethodCall(expression, "size");
  }

  private static boolean expressionIsListGetLookup(PsiExpression expression) {
    return isListMethodCall(expression, "get");
  }

  private static boolean isListMethodCall(final PsiExpression expression,
                                          @NonNls final String methodName) {
    final PsiExpression strippedExpression = ParenthesesUtils
      .stripParentheses(expression);
    if (!(strippedExpression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression reference = (PsiMethodCallExpression)strippedExpression;
    PsiReferenceExpression methodExpression = reference
      .getMethodExpression();
    if (methodExpression == null) {
      return false;
    }
    if (!(methodExpression
      .getQualifierExpression() instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)resolved;
    if (!methodName.equals(method.getName())) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    Project project = expression.getProject();
    PsiClass javaUtilList = PsiManager.getInstance(project)
      .findClass("java.util.List",
                 GlobalSearchScope.allScope(project));
    if (javaUtilList == null) {
      return false;
    }
    return InheritanceUtil.isInheritorOrSelf(aClass, javaUtilList, true);
  }

  private static boolean expressionIsArrayLengthLookup(
    PsiExpression expression) {
    final PsiExpression strippedExpression =
      ParenthesesUtils.stripParentheses(expression);
    if (!(strippedExpression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression reference =
      (PsiReferenceExpression)strippedExpression;
    @NonNls final String referenceName = reference.getReferenceName();
    if (!"length".equals(referenceName)) {
      return false;
    }
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiType type = qualifier.getType();
    if (type == null) {
      return false;
    }
    return type.getArrayDimensions() > 0;
  }

  private static boolean expressionIsVariableLookup(PsiExpression expression,
                                                    PsiLocalVariable var) {
    if (expression == null) {
      return false;
    }
    final PsiExpression strippedExpression =
      ParenthesesUtils.stripParentheses(expression);

    final String expressionText = strippedExpression.getText();
    final String varText = var.getName();
    return expressionText.equals(varText);
  }

  private static class VariableAssignmentVisitor
    extends PsiRecursiveElementVisitor {
    private boolean arrayAssigned = false;
    private final String arrayName;

    VariableAssignmentVisitor(String arrayName) {
      this.arrayName = arrayName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!arrayAssigned) {
        super.visitElement(element);
      }
    }

    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression exp) {
      if (arrayAssigned) {
        return;
      }
      super.visitAssignmentExpression(exp);
      final PsiExpression lhs = exp.getLExpression();
      if (arrayName.equals(lhs.getText())) {
        arrayAssigned = true;
      }
    }

    public boolean isArrayAssigned() {
      return arrayAssigned;
    }
  }

  private static class NumCallsToIteratorNextVisitor
    extends PsiRecursiveElementVisitor {
    private int numCallsToIteratorNext = 0;
    private final String iteratorName;

    NumCallsToIteratorNextVisitor(String iteratorName) {
      this.iteratorName = iteratorName;
    }

    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"next".equals(methodName)) {
        return;
      }

      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final String qualifierText = qualifier.getText();
      if (!iteratorName.equals(qualifierText)) {
        return;
      }
      numCallsToIteratorNext++;
    }

    public int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorAssignmentVisitor
    extends PsiRecursiveElementVisitor {
    private boolean iteratorAssigned = false;
    private final String iteratorName;

    IteratorAssignmentVisitor(String iteratorName) {
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!iteratorAssigned) {
        super.visitElement(element);
      }
    }

    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression exp) {
      if (iteratorAssigned) {
        return;
      }
      super.visitAssignmentExpression(exp);
      final PsiExpression lhs = exp.getLExpression();
      final String lhsText = lhs.getText();
      if (iteratorName.equals(lhsText)) {
        iteratorAssigned = true;
      }
    }

    public boolean isIteratorAssigned() {
      return iteratorAssigned;
    }
  }

  private static class IteratorRemoveVisitor
    extends PsiRecursiveElementVisitor {
    private boolean removeCalled = false;
    private final String iteratorName;

    IteratorRemoveVisitor(String iteratorName) {
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!removeCalled) {
        super.visitElement(element);
      }
    }

    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (removeCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"remove".equals(name)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier != null) {
        final String qualifierText = qualifier.getText();
        if (iteratorName.equals(qualifierText)) {
          removeCalled = true;
        }
      }
    }

    public boolean isRemoveCalled() {
      return removeCalled;
    }
  }

  private static class IteratorHasNextVisitor
    extends PsiRecursiveElementVisitor {
    private boolean hasNextCalled = false;
    private final String iteratorName;

    IteratorHasNextVisitor(String iteratorName) {
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!hasNextCalled) {
        super.visitElement(element);
      }
    }

    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (hasNextCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"hasNext".equals(name)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier != null) {
        final String qualifierText = qualifier.getText();
        if (iteratorName.equals(qualifierText)) {
          hasNextCalled = true;
        }
      }
    }

    public boolean isHasNextCalled() {
      return hasNextCalled;
    }
  }

  private static class IndexOnlyUsedAsIndexVisitor
    extends PsiRecursiveElementVisitor {
    private boolean indexVariableUsedOnlyAsIndex = true;
    private final String arrayName;
    private final PsiLocalVariable indexVariable;

    IndexOnlyUsedAsIndexVisitor(String arrayName,
                                PsiLocalVariable indexVariable) {
      this.arrayName = arrayName;
      this.indexVariable = indexVariable;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    public void visitReferenceExpression(
      @NotNull PsiReferenceExpression ref) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(ref);

      final PsiElement element = ref.resolve();
      if (!indexVariable.equals(element)) {
        return;
      }
      final PsiElement parent = ref.getParent();
      if (!(parent instanceof PsiArrayAccessExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiArrayAccessExpression arrayAccess =
        (PsiArrayAccessExpression)parent;
      final PsiExpression arrayExpression =
        arrayAccess.getArrayExpression();
      if (!arrayExpression.getText().equals(arrayName)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiElement arrayExpressionContext = arrayAccess.getParent();
      if (arrayExpressionContext instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignment =
          (PsiAssignmentExpression)arrayExpressionContext;
        final PsiExpression lhs = assignment.getLExpression();
        if (lhs.equals(arrayAccess)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex;
    }
  }

  private static class VariableOnlyUsedAsListIndexVisitor
    extends PsiRecursiveElementVisitor {
    private boolean indexVariableUsedOnlyAsIndex = true;
    private final PsiLocalVariable indexVariable;
    private final PsiVariable collection;

    VariableOnlyUsedAsListIndexVisitor(PsiVariable collection,
                                       PsiLocalVariable indexVariable) {
      this.collection = collection;
      this.indexVariable = indexVariable;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    public void visitReferenceExpression(
      @NotNull PsiReferenceExpression ref) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(ref);

      final PsiElement element = ref.resolve();
      if (indexVariable.equals(element) && !isListIndexExpression(ref,
                                                                  collection)) {
        indexVariableUsedOnlyAsIndex = false;
      }
      if (collection.equals(element) && !isListReferenceInIndexExpression(
        ref, collection)) {
        indexVariableUsedOnlyAsIndex = false;
      }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex;
    }
  }

  private static boolean isListIndexExpression(
    final PsiReferenceExpression ref, PsiVariable collection) {
    final PsiElement parent = ref.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiExpressionList expressionList = (PsiExpressionList)parent;
    if (!(expressionList
      .getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expressionList
      .getParent();
    return isListGetExpression(methodCallExpression, collection);
  }

  private static boolean isListReferenceInIndexExpression(
    final PsiReferenceExpression ref, PsiVariable collection) {
    final PsiElement parent = ref.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return false;
    }
    if (!(parent
      .getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent
      .getParent();
    return isListGetExpression(methodCallExpression, collection);
  }

  private static boolean isListGetExpression(
    final PsiMethodCallExpression methodCallExpression,
    final PsiVariable collection) {
    if (methodCallExpression == null) {
      return false;
    }
    PsiReferenceExpression methodExpression = methodCallExpression
      .getMethodExpression();
    if (methodExpression == null) {
      return false;
    }
    PsiExpression qualifierExpression = methodExpression
      .getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) {
      return false;
    }
    if (((PsiReferenceExpression)qualifierExpression).resolve()
        != collection) {
      return false;
    }
    return expressionIsListGetLookup(methodCallExpression);
  }
}
