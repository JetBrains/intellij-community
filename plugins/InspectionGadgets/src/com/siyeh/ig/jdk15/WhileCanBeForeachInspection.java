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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class WhileCanBeForeachInspection extends StatementInspection {

  private final WhileCanBeForeachFix fix = new WhileCanBeForeachFix();

  public String getID() {
    return "WhileLoopReplaceableByForEach";
  }

  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new WhileBeForeachVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class WhileCanBeForeachFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement whileElement = descriptor.getPsiElement();
      final PsiWhileStatement whileStatement =
        (PsiWhileStatement)whileElement.getParent();
      final String newExpression =
        createCollectionIterationText(whileStatement, project);
      final PsiStatement statement = getPreviousStatement(whileStatement);
      assert statement != null;
      deleteElement(statement);
      replaceStatementAndShortenClassNames(whileStatement, newExpression);
    }

    private String createCollectionIterationText(PsiWhileStatement whileStatement,
                                                 Project project)
      throws IncorrectOperationException {
      final int length = whileStatement.getText().length();
      @NonNls final StringBuffer out = new StringBuffer(length);
      final PsiStatement body = whileStatement.getBody();
      final PsiStatement firstStatement = getFirstStatement(body);
      final PsiStatement initialization =
        getPreviousStatement(whileStatement);
      final PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      assert declaration != null;
      final PsiLocalVariable iterator =
        (PsiLocalVariable)declaration.getDeclaredElements()[0];

      final PsiMethodCallExpression initializer =
        (PsiMethodCallExpression)iterator.getInitializer();
      final PsiExpression collection =
        initializer.getMethodExpression().getQualifierExpression();
      final PsiClassType type = (PsiClassType)collection.getType();
      final PsiType[] parameters = type.getParameters();
      final String contentTypeString;
      if (parameters.length == 1) {
        final PsiType parameterType = parameters[0];
        if (parameterType instanceof PsiWildcardType) {
          final PsiWildcardType wildcardType = (PsiWildcardType)parameterType;
          final PsiType bound = wildcardType.getBound();
          if (bound == null) {
            contentTypeString = "java.lang.Object";
          }
          else {
            contentTypeString = bound.getCanonicalText();
          }
        }
        else if (parameterType != null) {
          contentTypeString = parameterType.getCanonicalText();
        }
        else {
          contentTypeString = "java.lang.Object";
        }
      }
      else {
        contentTypeString = "java.lang.Object";
      }
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiElementFactory elementFactory =
        psiManager.getElementFactory();
      final PsiType contentType = elementFactory.createTypeFromText(contentTypeString,
                                                                    whileStatement);
      final String iteratorName = iterator.getName();
      final boolean isDeclaration =
        isIteratorNextDeclaration(firstStatement, iteratorName, contentTypeString);
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
            ((PsiReferenceExpression)collection).getReferenceName();
          contentVariableName = createNewVarName(project,
                                                 whileStatement,
                                                 contentType,
                                                 collectionName);
        }
        else {
          contentVariableName = createNewVarName(project,
                                                 whileStatement,
                                                 contentType, null);
        }
        if (CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      out.append("for(" + finalString + contentTypeString + ' ' +
                 contentVariableName + ": " + collection.getText() + ')');
      replaceIteratorNext(body, contentVariableName, iteratorName,
                          statementToSkip, out, contentTypeString);
      return out.toString();
    }

    private void replaceIteratorNext(PsiElement element,
                                     String contentVariableName,
                                     String iteratorName,
                                     PsiElement childToSkip,
                                     StringBuffer out,
                                     String contentTypeString) {

      if (isIteratorNext(element, iteratorName, contentTypeString)) {
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
                                  childToSkip, out, contentTypeString);
            }
          }
        }
      }
    }

    private boolean isIteratorNextDeclaration(PsiStatement statement,
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

    private boolean isIteratorNext(PsiElement element,
                                   String iteratorName, String contentType) {
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
      if (args.length != 0) {
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

    private String createNewVarName(Project project, PsiWhileStatement scope,
                                    PsiType type, String containerName) {
      final CodeStyleManager codeStyleManager =
        CodeStyleManager.getInstance(project);
      @NonNls String baseName;
      if (containerName != null) {
        baseName = StringUtils.createSingularFromName(containerName);
      }
      else {

        final SuggestedNameInfo suggestions =
          codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE,
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
    private PsiStatement getFirstStatement(PsiStatement body) {
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

  private static class WhileBeForeachVisitor
    extends StatementInspectionVisitor {

    public void visitWhileStatement(@NotNull PsiWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      final PsiManager manager = whileStatement.getManager();
      final LanguageLevel languageLevel =
        manager.getEffectiveLanguageLevel();
      if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
          languageLevel.equals(LanguageLevel.JDK_1_4)) {
        return;
      }
      if (!isCollectionLoopStatement(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }
  }

  private static boolean isCollectionLoopStatement(PsiWhileStatement whileStatement) {
    final PsiStatement initialization =
      getPreviousStatement(whileStatement);
    if (initialization == null) {
      return false;
    }
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
    if (declaredVar.getName() == null) {
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
    final PsiExpression qualifier =
      initialMethodExpression.getQualifierExpression();
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
        !ClassUtils.isSubclass(qualifierClass, "java.util.Collection")) {
      return false;
    }
    final String iteratorName = declaredVar.getName();
    final PsiExpression condition = whileStatement.getCondition();
    if (!isHasNext(condition, iteratorName)) {
      return false;
    }
    final PsiStatement body = whileStatement.getBody();
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

  @Nullable
  private static PsiStatement getPreviousStatement(PsiWhileStatement statement) {
    final PsiElement prevStatement =
      PsiTreeUtil.skipSiblingsBackward(statement,
                                       new Class[]{PsiWhiteSpace.class});
    if (prevStatement == null || !(prevStatement instanceof PsiStatement)) {
      return null;
    }
    return (PsiStatement)prevStatement;
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

  private static class NumCallsToIteratorNextVisitor
    extends PsiRecursiveElementVisitor {
    private int numCallsToIteratorNext = 0;
    private final String iteratorName;

    private NumCallsToIteratorNextVisitor(String iteratorName) {
      super();
      this.iteratorName = iteratorName;
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression callExpression) {
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

    private int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorAssignmentVisitor
    extends PsiRecursiveElementVisitor {
    private boolean iteratorAssigned = false;
    private final String iteratorName;

    private IteratorAssignmentVisitor(String iteratorName) {
      super();
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!iteratorAssigned) {
        super.visitElement(element);
      }
    }

    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression exp) {
      super.visitAssignmentExpression(exp);
      final PsiExpression lhs = exp.getLExpression();
      final String lhsText = lhs.getText();
      if (iteratorName.equals(lhsText)) {
        iteratorAssigned = true;
      }
    }

    private boolean isIteratorAssigned() {
      return iteratorAssigned;
    }
  }

  private static class IteratorRemoveVisitor
    extends PsiRecursiveElementVisitor {
    private boolean removeCalled = false;
    private final String iteratorName;

    private IteratorRemoveVisitor(String iteratorName) {
      super();
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!removeCalled) {
        super.visitElement(element);
      }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
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

    private boolean isRemoveCalled() {
      return removeCalled;
    }
  }

  private static class IteratorHasNextVisitor
    extends PsiRecursiveElementVisitor {
    private boolean hasNextCalled = false;
    private final String iteratorName;

    private IteratorHasNextVisitor(String iteratorName) {
      super();
      this.iteratorName = iteratorName;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!hasNextCalled) {
        super.visitElement(element);
      }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
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

    private boolean isHasNextCalled() {
      return hasNextCalled;
    }
  }
}
