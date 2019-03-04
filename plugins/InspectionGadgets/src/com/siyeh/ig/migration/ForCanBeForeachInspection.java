// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class ForCanBeForeachInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean REPORT_INDEXED_LOOP = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreUntypedCollections;

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

  static boolean isIndexedListLoopStatement(PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
    final PsiElement[] declaredElements = declaration.getDeclaredElements();
    final PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable indexVariable = (PsiVariable)declaredElement;
    final PsiExpression initialValue = indexVariable.getInitializer();
    if (initialValue == null) {
      return false;
    }
    final Object constant = ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Number)) {
      return false;
    }
    final Number number = (Number)constant;
    if (number.intValue() != 0) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    final Holder collectionHolder = getCollectionFromSizeComparison(condition, indexVariable, secondDeclaredElement);
    if (collectionHolder == null) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    final PsiStatement body = forStatement.getBody();
    if (!isIndexVariableOnlyUsedAsListIndex(collectionHolder, indexVariable, body)) {
      return false;
    }
    if (collectionHolder != Holder.DUMMY) {
      final PsiVariable collection = collectionHolder.getVariable();
      final PsiClassType collectionType = (PsiClassType)collection.getType();
      final PsiType[] parameters = collectionType.getParameters();
      if (ignoreUntypedCollections && parameters.length == 0) {
        return false;
      }
      return !VariableAccessUtils.variableIsAssigned(collection, body);
    }
    return true;
  }

  static boolean isArrayLoopStatement(PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
    final PsiElement[] declaredElements = declaration.getDeclaredElements();
    final PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable indexVariable = (PsiVariable)declaredElement;
    final PsiExpression initialValue = indexVariable.getInitializer();
    if (initialValue == null) {
      return false;
    }
    final Object constant = ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Integer)) {
      return false;
    }
    final Integer integer = (Integer)constant;
    if (integer.intValue() != 0) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    final PsiReferenceExpression arrayReference = getVariableReferenceFromCondition(condition, indexVariable, secondDeclaredElement);
    if (arrayReference == null) {
      return false;
    }
    if (!(arrayReference.getType() instanceof PsiArrayType)) {
      return false;
    }
    final PsiElement element = arrayReference.resolve();
    if (!(element instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable arrayVariable = (PsiVariable)element;
    final PsiStatement body = forStatement.getBody();
    return body == null ||
           isIndexVariableOnlyUsedAsIndex(arrayVariable, indexVariable, body) &&
           !VariableAccessUtils.variableIsAssigned(arrayVariable, body) &&
           !VariableAccessUtils.arrayContentsAreAssigned(arrayVariable, body);
  }

  private static boolean isIndexVariableOnlyUsedAsIndex(
    @NotNull PsiVariable arrayVariable,
    @NotNull PsiVariable indexVariable,
    @Nullable PsiStatement body) {
    if (body == null) {
      return true;
    }
    final VariableOnlyUsedAsIndexVisitor visitor =
      new VariableOnlyUsedAsIndexVisitor(arrayVariable, indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  private static boolean isIndexVariableOnlyUsedAsListIndex(
    Holder collectionHolder, PsiVariable indexVariable,
    PsiStatement body) {
    if (body == null) {
      return true;
    }
    final VariableOnlyUsedAsListIndexVisitor visitor =
      new VariableOnlyUsedAsListIndexVisitor(collectionHolder,
                                             indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  static boolean isCollectionLoopStatement(PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    final PsiStatement initialization = forStatement.getInitialization();
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
    final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
    @NonNls final String initialCallName = initialMethodExpression.getReferenceName();
    if (!HardcodedMethodConstants.ITERATOR.equals(initialCallName) && !"listIterator".equals(initialCallName)) {
      return false;
    }
    if (!initialCall.getArgumentList().isEmpty()) {
      return false;
    }
    final PsiExpression qualifier = ExpressionUtils.getQualifierOrThis(initialMethodExpression);
    final PsiType qualifierType = qualifier.getType();
    if (!(qualifierType instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)qualifierType;
    final PsiClass qualifierClass = classType.resolve();
    if (ignoreUntypedCollections) {
      final PsiClassType type = (PsiClassType)variable.getType();
      final PsiType[] parameters = type.getParameters();
      final PsiType[] parameters1 = classType.getParameters();
      if (parameters.length == 0 && parameters1.length == 0) {
        return false;
      }
    }
    if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!isHasNext(condition, variable)) {
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
    if (calculateCallsToIteratorNext(variable, body) != 1) {
      return false;
    }
    if (isIteratorMethodCalled(variable, body)) {
      return false;
    }
    return !VariableAccessUtils.variableIsReturned(variable, body) &&
           !VariableAccessUtils.variableIsAssigned(variable, body) &&
           !VariableAccessUtils.variableIsPassedAsMethodArgument(variable, body);
  }

  private static int calculateCallsToIteratorNext(PsiVariable iterator,
                                                  PsiStatement body) {
    if (body == null) {
      return 0;
    }
    final NumCallsToIteratorNextVisitor visitor = new NumCallsToIteratorNextVisitor(iterator);
    body.accept(visitor);
    return visitor.getNumCallsToIteratorNext();
  }

  public static boolean isIteratorMethodCalled(PsiVariable iterator, PsiStatement body) {
    final IteratorMethodCallVisitor visitor = new IteratorMethodCallVisitor(iterator);
    body.accept(visitor);
    return visitor.isMethodCalled();
  }

  static boolean isHasNext(PsiExpression condition,
                           PsiVariable iterator) {
    if (!(condition instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
    if (!call.getArgumentList().isEmpty()) {
      return false;
    }
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
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

  @Nullable
  private static PsiReferenceExpression getVariableReferenceFromCondition(PsiExpression condition,
                                                                          PsiVariable variable,
                                                                          PsiElement secondDeclaredElement) {
    final PsiBinaryExpression binaryExpression = tryCast(ParenthesesUtils.stripParentheses(condition), PsiBinaryExpression.class);
    if (binaryExpression == null) return null;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
    final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
    if (lhs == null || rhs == null) return null;
    PsiReferenceExpression referenceExpression;
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!ExpressionUtils.isReferenceTo(lhs, variable) || !(rhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)rhs;
    }
    else if (tokenType.equals(JavaTokenType.GT)) {
      if (!ExpressionUtils.isReferenceTo(rhs, variable) || !(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)lhs;
    }
    else {
      return null;
    }
    if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
      final PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) return null;
      if (target instanceof PsiVariable) {
        final PsiVariable maxVariable = (PsiVariable)target;
        final PsiCodeBlock context = PsiTreeUtil.getParentOfType(maxVariable, PsiCodeBlock.class);
        if (context == null || VariableAccessUtils.variableIsAssigned(maxVariable, context)) return null;
        final PsiExpression expression = ParenthesesUtils.stripParentheses(maxVariable.getInitializer());
        if (!(expression instanceof PsiReferenceExpression)) return null;
        referenceExpression = (PsiReferenceExpression)expression;
        if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
          return null;
        }
      }
    }
    else {
      if (secondDeclaredElement != null) return null;
    }
    final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      return (PsiReferenceExpression)qualifierExpression;
    }
    else if (qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression ||
             qualifierExpression == null) {
      return referenceExpression;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static Holder getCollectionFromSizeComparison(PsiExpression condition, PsiVariable variable, PsiElement secondDeclaredElement) {
    condition = ParenthesesUtils.stripParentheses(condition);
    if (!(condition instanceof PsiBinaryExpression)) {
      return null;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final PsiExpression rhs = binaryExpression.getROperand();
    final PsiExpression lhs = binaryExpression.getLOperand();
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!ExpressionUtils.isReferenceTo(lhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(rhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    if (tokenType.equals(JavaTokenType.GT)) {
      if (!ExpressionUtils.isReferenceTo(rhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(lhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    return null;
  }

  static boolean expressionIsListGetLookup(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression reference =
      (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression =
      reference.getMethodExpression();
    final PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)resolved;
    if (!HardcodedMethodConstants.GET.equals(method.getName())) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return InheritanceUtil.isInheritor(aClass,
                                       CommonClassNames.JAVA_UTIL_LIST);
  }

  @Nullable
  private static Holder getCollectionFromListMethodCall(PsiExpression expression, String methodName, PsiElement secondDeclaredElement) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) {
        return null;
      }
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      final PsiVariable variable = (PsiVariable)target;
      final PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (context == null) {
        return null;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return null;
      }
      expression = ParenthesesUtils.stripParentheses(variable.getInitializer());
    }
    else if (secondDeclaredElement !=  null) {
      return null;
    }
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    final String referenceName = methodExpression.getReferenceName();
    if (!methodName.equals(referenceName)) {
      return null;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritor(containingClass,
                                     CommonClassNames.JAVA_UTIL_LIST)) {
      return null;
    }
    final PsiExpression qualifierExpression =
      ParenthesesUtils.stripParentheses(
        methodExpression.getQualifierExpression());
    if (qualifierExpression == null ||
        qualifierExpression instanceof PsiThisExpression ||
        qualifierExpression instanceof PsiSuperExpression) {
      return Holder.DUMMY;
    }
    if (!(qualifierExpression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifierExpression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return null;
    }
    final PsiVariable variable = (PsiVariable)target;
    return new Holder(variable);
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ForLoopReplaceableByForEach";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "for.can.be.foreach.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "for.can.be.foreach.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForCanBeForeachVisitor();
  }

  @Nullable
  static PsiType getContentType(PsiType type, String containerClassName) {
    PsiType parameterType = PsiUtil.substituteTypeParameter(type, containerClassName, 0, true);
    return GenericsUtil.getVariableTypeByExpressionType(parameterType);
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
    final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
    if (!callExpression.getArgumentList().isEmpty()) {
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
    return new VariableNameGenerator(scope, VariableKind.PARAMETER).byCollectionName(containerName).byType(type)
      .byName("value", "item", "element").generate(true);
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

  private static class NumCallsToIteratorNextVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private int numCallsToIteratorNext;
    private final PsiVariable iterator;

    private NumCallsToIteratorNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!iterator.equals(target)) {
        return;
      }
      numCallsToIteratorNext++;
    }

    private int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorMethodCallVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean methodCalled;
    private final PsiVariable iterator;

    private IteratorMethodCallVisitor(PsiVariable iterator) {
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
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        methodCalled = true;
      }
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (methodCalled) {
        return;
      }
      super.visitMethodReferenceExpression(expression);
      final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(expression.getQualifierExpression());
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
      if (iterator.equals(referenceExpression.resolve())) {
        methodCalled = true;
      }
    }

    private boolean isMethodCalled() {
      return methodCalled;
    }
  }

  private static class VariableOnlyUsedAsIndexVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean arrayAccessed = false;
    private final PsiVariable arrayVariable;
    private final PsiVariable indexVariable;

    private VariableOnlyUsedAsIndexVisitor(PsiVariable arrayVariable,
                                           PsiVariable indexVariable) {
      this.arrayVariable = arrayVariable;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      final PsiElement element = reference.resolve();
      if (!indexVariable.equals(element)) {
        return;
      }
      final PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiArrayAccessExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parent;
      final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      if (!(arrayExpression instanceof PsiReferenceExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arrayExpression;
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!arrayVariable.equals(target)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      arrayAccessed = true;
      final PsiElement arrayExpressionContext = arrayAccessExpression.getParent();
      if (arrayExpressionContext instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression)arrayExpressionContext;
        final PsiExpression lhs = assignment.getLExpression();
        if (lhs.equals(arrayAccessExpression)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
    }

    private boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex && arrayAccessed;
    }
  }

  private static class VariableOnlyUsedAsListIndexVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean listGetCalled;
    private final PsiVariable indexVariable;
    private final Holder collection;

    private VariableOnlyUsedAsListIndexVisitor(
      @NotNull Holder collection,
      @NotNull PsiVariable indexVariable) {
      this.collection = collection;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(
      @NotNull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      final PsiElement element = reference.resolve();
      if (indexVariable.equals(element)) {
        if (!isListIndexExpression(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
        else {
          listGetCalled = true;
        }
      }
      else if (collection == Holder.DUMMY) {
        if (isListNonGetMethodCall(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
      else if (collection.getVariable().equals(element) &&
               !isListReferenceInIndexExpression(reference)) {
        indexVariableUsedOnlyAsIndex = false;
      }
    }

    private boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex && listGetCalled;
    }

    private boolean isListNonGetMethodCall(
      PsiReferenceExpression reference) {
      final PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent;
      final PsiMethod method =
        methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(
        methodCallExpression, PsiClass.class);
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritorOrSelf(parentClass,
                                             containingClass, true)) {
        return false;
      }
      return !isListGetExpression(methodCallExpression);
    }

    private boolean isListIndexExpression(PsiReferenceExpression reference) {
      final PsiElement referenceParent = reference.getParent();
      if (!(referenceParent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiExpressionList expressionList =
        (PsiExpressionList)referenceParent;
      final PsiElement parent = expressionList.getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent;
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListReferenceInIndexExpression(
      PsiReferenceExpression reference) {
      final PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      final PsiElement greatGrandParent =
        methodCallExpression.getParent();
      if (greatGrandParent instanceof PsiExpressionStatement) {
        return false;
      }
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListGetExpression(
      PsiMethodCallExpression methodCallExpression) {
      if (methodCallExpression == null) {
        return false;
      }
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        if (collection == Holder.DUMMY &&
            (qualifierExpression == null ||
             qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression)) {
          return expressionIsListGetLookup(methodCallExpression);
        }
        return false;
      }
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)qualifierExpression;
      final PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)
          && !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      final PsiElement target = reference.resolve();
      if (collection == Holder.DUMMY ||
          !collection.getVariable().equals(target)) {
        return false;
      }
      return expressionIsListGetLookup(methodCallExpression);
    }
  }

  private static class Holder {

    public static final Holder DUMMY = new Holder();

    private final PsiVariable variable;

    Holder(@NotNull PsiVariable variable) {
      this.variable = variable;
    }

    private Holder() {
      variable = null;
    }

    public PsiVariable getVariable() {
      return variable;
    }
  }

  private class ForCanBeForeachVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement forStatement) {
      super.visitForStatement(forStatement);
      if (isArrayLoopStatement(forStatement) || isCollectionLoopStatement(forStatement, ignoreUntypedCollections) ||
          REPORT_INDEXED_LOOP && isIndexedListLoopStatement(forStatement, ignoreUntypedCollections)) {
        registerStatementError(forStatement);
      }
    }
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
      final PsiBinaryExpression condition = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(forStatement.getCondition());
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
      final PsiType type = qualifier.getType();
      if (type == null) {
        return null;
      }
      PsiType parameterType = getContentType(type, CommonClassNames.JAVA_UTIL_COLLECTION);
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(forStatement);
      }
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
        parameterType = variable.getType();
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
      out.append(finalString).append(parameterType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
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
      final PsiVariable iterator = (PsiVariable)declaredIterator;
      final PsiMethodCallExpression initializer = (PsiMethodCallExpression)iterator.getInitializer();
      if (initializer == null) {
        return null;
      }
      final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      final PsiExpression collection = ExpressionUtils.getQualifierOrThis(methodExpression);
      final PsiType collectionType = collection.getType();
      if (collectionType == null) {
        return null;
      }
      final PsiType contentType = getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
      if (contentType == null) {
        return null;
      }
      PsiType iteratorContentType = getContentType(iterator.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      if (TypeUtils.isJavaLangObject(iteratorContentType)) {
        iteratorContentType = getContentType(initializer.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
      }
      if (iteratorContentType == null) {
        return null;
      }

      final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
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
        iteratorContentType = variable.getType();
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
        if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      final String contentTypeString = iteratorContentType.getCanonicalText();
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(contentTypeString);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        out.append('(').append("java.lang.Iterable<").append(contentTypeString).append('>').append(')');
      }
      out.append(collection.getText());
      out.append(')');
      replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, out);
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
      PsiType componentType = arrayType.getComponentType();
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
        componentType = variable.getType();
        if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
          final String collectionName = arrayReference.getReferenceName();
          contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
          if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
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
        if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
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
      out.append(componentType.getCanonicalText());
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
      return isListGetLookup(initializer, indexName, listVariable);
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
}
