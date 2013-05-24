/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhileCanBeForeachInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "WhileLoopReplaceableByForEach";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("while.can.be.foreach.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("while.can.be.foreach.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new WhileCanBeForeachFix();
  }

  private static class WhileCanBeForeachFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement whileElement = descriptor.getPsiElement();
      final PsiWhileStatement whileStatement = (PsiWhileStatement)whileElement.getParent();
      replaceWhileWithForEach(whileStatement);
    }

    private static void replaceWhileWithForEach(@NotNull PsiWhileStatement whileStatement) {
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      if (declaration == null) {
        return;
      }
      final PsiElement declaredElement = declaration.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable iterator = (PsiLocalVariable)declaredElement;
      final PsiMethodCallExpression initializer = (PsiMethodCallExpression)iterator.getInitializer();
      if (initializer == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      final PsiExpression collection = methodExpression.getQualifierExpression();
      if (collection == null) {
        return;
      }
      final PsiType contentType = getContentType(collection.getType(), collection);
      if (contentType == null) {
        return;
      }
      final Project project = whileStatement.getProject();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        if (declarationStatement == null) {
          return;
        }
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVariable.getName();
        statementToSkip = declarationStatement;
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)collection;
          final String collectionName = referenceElement.getReferenceName();
          contentVariableName = createNewVariableName(whileStatement, contentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(whileStatement, contentType, null);
        }
        statementToSkip = null;
      }
      final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      if (codeStyleSettings.GENERATE_FINAL_PARAMETERS) {
        out.append("final ");
      }
      final PsiType iteratorContentType = getContentType(iterator.getType(), iterator);
      if (iteratorContentType == null) {
        return;
      }
      out.append(iteratorContentType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        out.append("(java.lang.Iterable<").append(iteratorContentType.getCanonicalText()).append(">)");
      }
      out.append(collection.getText()).append(')');

      replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, out);
      final Query<PsiReference> query = ReferencesSearch.search(iterator, iterator.getUseScope());
      boolean deleteIterator = true;
      for (PsiReference usage : query) {
        final PsiElement element = usage.getElement();
        if (PsiTreeUtil.isAncestor(whileStatement, element, true)) {
          continue;
        }
        final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
        if (assignment == null) {
          // iterator is read after while loop,
          // so cannot be deleted
          deleteIterator = false;
          break;
        }
        final PsiExpression expression = assignment.getRExpression();
        initializer.delete();
        iterator.setInitializer(expression);
        final PsiElement statement = assignment.getParent();
        final PsiElement lastChild = statement.getLastChild();
        if (lastChild instanceof PsiComment) {
          iterator.add(lastChild);
        }
        statement.replace(iterator);
        break;
      }
      if (deleteIterator) {
        iterator.delete();
      }
      final String result = out.toString();
      replaceStatementAndShortenClassNames(whileStatement, result);
    }

    @Nullable
    private static PsiType getContentType(PsiType type, PsiElement context) {
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiType[] parameters = classType.getParameters();
      if (parameters.length == 1) {
        final PsiType parameterType = parameters[0];
        if (parameterType instanceof PsiCapturedWildcardType) {
          final PsiCapturedWildcardType wildcardType = (PsiCapturedWildcardType)parameterType;
          final PsiType bound = wildcardType.getUpperBound();
          if (bound != null) {
            return bound;
          }
        }
        else if (parameterType != null) {
          return parameterType;
        }
      }
      return TypeUtils.getObjectType(context);
    }

    private static void replaceIteratorNext(@NotNull PsiElement element, String contentVariableName, PsiVariable iterator,
                                            PsiType contentType, PsiElement childToSkip, StringBuilder out) {
      if (isIteratorNext(element, iterator, contentType)) {
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
            if (shouldSkip(iterator, contentType, child)) {
              skippingWhiteSpace = true;
            }
            else if (child.equals(childToSkip)) {
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

    private static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
      if (!(child instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)child;
      final PsiExpression expression = expressionStatement.getExpression();
      return isIteratorNext(expression, iterator, contentType);
    }

    private static boolean isIteratorNextDeclaration(PsiStatement statement, PsiVariable iterator, PsiType contentType) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      final PsiElement[] elements = declarationStatement.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      final PsiElement element = elements[0];
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      final PsiExpression initializer = variable.getInitializer();
      return isIteratorNext(initializer, iterator, contentType);
    }

    private static boolean isIteratorNext(PsiElement element, PsiVariable iterator, PsiType contentType) {
      if (element == null) {
        return false;
      }
      if (element instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
        final PsiType type = castExpression.getType();
        if (type == null) {
          return false;
        }
        if (!type.equals(contentType)) {
          return false;
        }
        final PsiExpression operand = castExpression.getOperand();
        return isIteratorNext(operand, iterator, contentType);
      }
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      final PsiReferenceExpression reference = callExpression.getMethodExpression();
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

    private static String createNewVariableName(@NotNull PsiWhileStatement scope, PsiType type, String containerName) {
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
      if (baseName == null || baseName.length() == 0) {
        baseName = "value";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
    }

    @Nullable
    private static PsiStatement getFirstStatement(@NotNull PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        return ArrayUtil.getFirstElement(codeBlock.getStatements());
      }
      else {
        return body;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileCanBeForeachVisitor();
  }

  private static class WhileCanBeForeachVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      if (!PsiUtil.isLanguageLevel5OrHigher(whileStatement)) {
        return;
      }
      if (!isCollectionLoopStatement(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }

    private static boolean isCollectionLoopStatement(PsiWhileStatement whileStatement) {
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)declaredElement;
      if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR, "java.util.ListIterator")) {
        return false;
      }
      final PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) {
        return false;
      }
      if (!(initialValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression initialCall = (PsiMethodCallExpression)initialValue;
      final PsiExpressionList argumentList = initialCall.getArgumentList();
      final PsiExpression[] argument = argumentList.getExpressions();
      if (argument.length != 0) {
        return false;
      }
      final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
      @NonNls final String initialCallName = initialMethodExpression.getReferenceName();
      if (!"iterator".equals(initialCallName) && !"listIterator".equals(initialCallName)) {
        return false;
      }
      final PsiExpression qualifier = initialMethodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return false;
      }
      final PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass == null) {
        return false;
      }
      if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE) &&
          !InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        return false;
      }
      final PsiExpression condition = whileStatement.getCondition();
      if (!isHasNextCalled(variable, condition)) {
        return false;
      }
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return false;
      }
      if (calculateCallsToIteratorNext(variable, body) != 1) {
        return false;
      }
      if (isIteratorRemoveCalled(variable, body)) {
        return false;
      }
      //noinspection SimplifiableIfStatement
      if (isIteratorHasNextCalled(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, body)) {
        return false;
      }
      PsiElement nextSibling = whileStatement.getNextSibling();
      while (nextSibling != null) {
        if (VariableAccessUtils.variableValueIsUsed(variable, nextSibling)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }

    private static boolean isHasNextCalled(PsiVariable iterator, PsiExpression condition) {
      if (!(condition instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return true;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      return iterator.equals(target);
    }

    private static int calculateCallsToIteratorNext(PsiVariable iterator, PsiElement context) {
      final NumCallsToIteratorNextVisitor visitor = new NumCallsToIteratorNextVisitor(iterator);
      context.accept(visitor);
      return visitor.getNumCallsToIteratorNext();
    }

    private static boolean isIteratorRemoveCalled(PsiVariable iterator, PsiElement context) {
      final IteratorMethodCallVisitor visitor = new IteratorMethodCallVisitor(iterator);
      context.accept(visitor);
      return visitor.isMethodCalled();
    }

    private static boolean isIteratorHasNextCalled(PsiVariable iterator, PsiElement context) {
      final IteratorHasNextVisitor visitor = new IteratorHasNextVisitor(iterator);
      context.accept(visitor);
      return visitor.isHasNextCalled();
    }
  }

  @Nullable
  public static PsiStatement getPreviousStatement(PsiElement context) {
    final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(context, PsiWhiteSpace.class, PsiComment.class);
    if (!(prevStatement instanceof PsiStatement)) {
      return null;
    }
    return (PsiStatement)prevStatement;
  }

  private static class NumCallsToIteratorNextVisitor extends JavaRecursiveElementVisitor {

    private int numCallsToIteratorNext = 0;
    private final PsiVariable iterator;

    private NumCallsToIteratorNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(methodName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!iterator.equals(target)) {
        return;
      }
      numCallsToIteratorNext++;
    }

    public int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorMethodCallVisitor extends JavaRecursiveElementVisitor {

    private boolean methodCalled = false;
    private final PsiVariable iterator;

    IteratorMethodCallVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!methodCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (methodCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.NEXT.equals(name)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        methodCalled = true;
      }
    }

    public boolean isMethodCalled() {
      return methodCalled;
    }
  }

  private static class IteratorHasNextVisitor extends JavaRecursiveElementVisitor {

    private boolean hasNextCalled = false;
    private final PsiVariable iterator;

    private IteratorHasNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!hasNextCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(name)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        hasNextCalled = true;
      }
    }

    public boolean isHasNextCalled() {
      return hasNextCalled;
    }
  }
}