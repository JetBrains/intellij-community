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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

public class TooBroadScopeInspection extends BaseInspection {

  /**
   * @noinspection PublicField for externalization
   */
  public boolean m_allowConstructorAsInitializer = false;

  /**
   * @noinspection PublicField for externalization
   */
  public boolean m_onlyLookAtBlocks = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("too.broad.scope.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "TooBroadScope";
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel checkboxOptionsPanel = new MultipleCheckboxOptionsPanel(this);
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message("too.broad.scope.only.blocks.option"), "m_onlyLookAtBlocks");
    checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message("too.broad.scope.allow.option"), "m_allowConstructorAsInitializer");
    return checkboxOptionsPanel;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("too.broad.scope.problem.descriptor");
  }

  protected boolean isMovable(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return true;
    }
    if (PsiUtil.isConstantExpression(expression) || ExpressionUtils.isNullLiteral(expression)) {
      return true;
    }
    if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
        if (!isMovable(initializer)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
      if (arrayDimensions.length > 0) {
        for (PsiExpression arrayDimension : arrayDimensions) {
          if (!isMovable(arrayDimension)) {
            return false;
          }
        }
        return true;
      }
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      if (arrayInitializer != null) {
        final PsiExpression[] initializers = arrayInitializer.getInitializers();
        for (final PsiExpression initializerExpression : initializers) {
          if (!isMovable(initializerExpression)) {
            return false;
          }
        }
        return true;
      }
      final PsiType type = newExpression.getType();
      if (type == null) {
        return false;
      }
      else if (!m_allowConstructorAsInitializer) {
        if (!isAllowedType(type)) {
          return false;
        }
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (final PsiExpression argumentExpression : expressions) {
        if (!isMovable(argumentExpression)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (!isMovable(qualifier)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiClass) {
        return true;
      }
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)target;
      if (!ClassUtils.isImmutable(variable.getType()) && !CollectionUtils.isEmptyArray(variable)) {
        return false;
      }
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      final PsiElement context = PsiUtil.getVariableCodeBlock(variable, referenceExpression);
      return context != null && !(variable instanceof PsiField) &&
             HighlightControlFlowUtil.isEffectivelyFinal(variable, context, referenceExpression);
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (!isMovable(operand)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiMethodCallExpression) {
      if (!isAllowedType(expression.getType())) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (!isAllowedMethod(method)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null && !isMovable(qualifierExpression)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      for (PsiExpression argument : argumentList.getExpressions()){
        if (!isMovable(argument)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return new TooBroadScopeInspectionFix(variable.getName());
  }

  private static boolean isAllowedMethod(PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null || !qualifiedName.startsWith("java.") || qualifiedName.equals("java.lang.Thread")) {
      return false;
    }
    final String methodName = method.getName();
    return !"now".equals(methodName) && !"currentTimeMillis".equals(methodName) &&
           !"nanoTime".equals(methodName) && !"waitFor".equals(methodName);
  }

  private static boolean isAllowedType(PsiType type) {
    if (ClassUtils.isImmutable(type)) {
      return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return isAllowedClass(aClass);
  }

  private static boolean isAllowedClass(@Nullable PsiClass aClass) {
    // allow some "safe" jdk types
    if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
        InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
      return true;
    }
    return aClass != null && aClass.isEnum();
  }

  static List<PsiReferenceExpression> findReferences(@NotNull PsiLocalVariable variable) {
    final List<PsiReferenceExpression> result = new SmartList<>();
    ReferencesSearch.search(variable, variable.getUseScope())
                    .forEach(reference -> reference instanceof PsiReferenceExpression && result.add((PsiReferenceExpression)reference));
    return result;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadScopeVisitor();
  }

  private class TooBroadScopeVisitor extends BaseInspectionVisitor {

    TooBroadScopeVisitor() {}

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (variable instanceof PsiResourceVariable) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (!isMovable(initializer)) {
        return;
      }
      final List<PsiReferenceExpression> references = findReferences(variable);
      if (references.isEmpty()) {
        return;
      }
      PsiElement commonParent = ScopeUtils.getCommonParent(references);
      if (commonParent == null) {
        return;
      }
      final PsiElement variableScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class);
      if (variableScope == null) {
        return;
      }
      if (initializer != null) {
        commonParent = ScopeUtils.moveOutOfLoopsAndClasses(commonParent, variableScope);
        if (commonParent == null) {
          return;
        }
      }
      if (PsiTreeUtil.isAncestor(commonParent, variableScope, true)) {
        return;
      }
      if (PsiTreeUtil.isAncestor(variableScope, commonParent, true)) {
        registerVariableError(variable, variable);
        return;
      }
      if (m_onlyLookAtBlocks) {
        return;
      }
      if (commonParent instanceof PsiForStatement) {
        return;
      }
      final PsiElement referenceElement = references.get(0);
      final PsiElement blockChild = ScopeUtils.getChildWhichContainsElement(variableScope, referenceElement);
      if (blockChild == null) {
        return;
      }
      final PsiElement insertionPoint = ScopeUtils.findTighterDeclarationLocation(blockChild, variable);
      if (insertionPoint == null) {
        if (!(blockChild instanceof PsiExpressionStatement)) {
          return;
        }
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)blockChild;
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) {
          return;
        }
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
        final IElementType tokenType = assignmentExpression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQ) {
          return;
        }
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!lhs.equals(referenceElement)) {
          return;
        }
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (rhs != null && VariableAccessUtils.variableIsUsed(variable, rhs)) {
          return;
        }
      }
      if (insertionPoint != null && FileTypeUtils.isInServerPageFile(insertionPoint)) {
        PsiElement elementBefore = insertionPoint.getPrevSibling();
        elementBefore = PsiTreeUtil.skipWhitespacesBackward(elementBefore);
        if (elementBefore instanceof PsiDeclarationStatement) {
          final PsiElement variableParent = variable.getParent();
          if (elementBefore.equals(variableParent)) {
            return;
          }
        }
      }
      registerVariableError(variable, variable);
    }
  }

  private class TooBroadScopeInspectionFix extends InspectionGadgetsFix {

    private final String variableName;

    TooBroadScopeInspectionFix(String variableName) {
      this.variableName = variableName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", variableName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Narrow scope";
    }

    @Override
    protected void doFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiElement variableIdentifier = descriptor.getPsiElement();
      if (!(variableIdentifier instanceof PsiIdentifier)) {
        return;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)variableIdentifier.getParent();
      assert variable != null;
      final PsiElement variableScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class, PsiTryStatement.class);
      final List<PsiReferenceExpression> references = findReferences(variable);
      PsiElement commonParent = ScopeUtils.getCommonParent(references);
      if (commonParent == null) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        assert variableScope != null;
        commonParent = ScopeUtils.moveOutOfLoopsAndClasses(commonParent, variableScope);
        if (commonParent == null) {
          return;
        }
      }
      final PsiElement referenceElement = references.get(0);
      final PsiElement firstReferenceScope = PsiTreeUtil.getParentOfType(referenceElement, PsiCodeBlock.class, PsiForStatement.class, PsiTryStatement.class);
      if (firstReferenceScope == null) {
        return;
      }
      PsiElement newDeclaration;
      CommentTracker tracker = new CommentTracker();
      if (commonParent instanceof PsiTryStatement) {
        PsiElement resourceReference = referenceElement.getParent();
        if (initializer != null) {
          tracker.markUnchanged(initializer);
        }
        PsiResourceVariable resourceVariable = JavaPsiFacade.getElementFactory(project).createResourceVariable(
          Objects.requireNonNull(variable.getName()), variable.getType(), initializer, variable);
        newDeclaration = resourceReference.getParent().addBefore(resourceVariable, resourceReference);
        resourceReference.delete();
      }
      else if (commonParent instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)commonParent;
        final PsiStatement initialization = forStatement.getInitialization();
        if (initialization == null) {
          return;
        }
        if (initialization instanceof PsiExpressionStatement) {
          final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)initialization;
          final PsiExpression expression = expressionStatement.getExpression();
          if (!(expression instanceof PsiAssignmentExpression)) {
            return;
          }
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
          final PsiExpression rhs = assignmentExpression.getRExpression();
          newDeclaration = createNewDeclaration(variable, rhs, tracker);
        }
        else {
          newDeclaration = createNewDeclaration(variable, initializer, tracker);
        }
        newDeclaration = initialization.replace(newDeclaration);
      } else if (firstReferenceScope.equals(commonParent)) {
        newDeclaration = moveDeclarationToLocation(variable, referenceElement, tracker);
      }
      else {
        final PsiElement commonParentChild = ScopeUtils.getChildWhichContainsElement(commonParent, referenceElement);
        if (commonParentChild == null) {
          return;
        }
        final PsiElement location = commonParentChild.getPrevSibling();
        newDeclaration = createNewDeclaration(variable, initializer, tracker);
        newDeclaration = commonParent.addAfter(newDeclaration, location);
      }
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      newDeclaration = codeStyleManager.reformat(newDeclaration);
      removeOldVariable(variable, tracker);
      tracker.insertCommentsBefore(newDeclaration);
      if (isOnTheFly()) {
        HighlightUtils.highlightElement(newDeclaration);
      }
    }

    private void removeOldVariable(@NotNull PsiVariable variable, CommentTracker tracker) {
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      if (declaration == null) {
        return;
      }
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length == 1) {
        tracker.delete(declaration);
      }
      else {
        tracker.delete(variable);
      }
    }

    private PsiDeclarationStatement createNewDeclaration(@NotNull PsiVariable variable,
                                                         @Nullable PsiExpression initializer,
                                                         CommentTracker tracker) {
      final Project project = variable.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      String name = variable.getName();
      if (name == null) {
        name = "";
      }

      final PsiType type = variable.getType();
      final PsiDeclarationStatement newDeclaration = factory.createVariableDeclarationStatement(name, type, initializer != null ? tracker.markUnchanged(initializer) : null, variable);
      final PsiLocalVariable newVariable = (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
      final PsiModifierList newModifierList = newVariable.getModifierList();
      final PsiModifierList modifierList = variable.getModifierList();
      if (newModifierList != null && modifierList != null) {
        // remove final when PsiDeclarationFactory adds one by mistake
        newModifierList.setModifierProperty(PsiModifier.FINAL, variable.hasModifierProperty(PsiModifier.FINAL));
        GenerateMembersUtil.copyAnnotations(modifierList, newModifierList);
      }
      PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.isInferredType()) {
        //restore 'var' for local variables
        newVariable.getTypeElement().replace(factory.createTypeElementFromText("var", variable));
      }
      return newDeclaration;
    }


    private PsiDeclarationStatement moveDeclarationToLocation(@NotNull PsiVariable variable,
                                                              @NotNull PsiElement location,
                                                              CommentTracker tracker) {
      PsiStatement statement = PsiTreeUtil.getParentOfType(location, PsiStatement.class, false);
      assert statement != null;
      PsiElement statementParent = statement.getParent();
      while (statementParent instanceof PsiStatement && !(statementParent instanceof PsiForStatement)) {
        statement = (PsiStatement)statementParent;
        statementParent = statement.getParent();
      }
      assert statementParent != null;
      final PsiExpression initializer = variable.getInitializer();
      if (isMovable(initializer) && statement instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
        final PsiExpression expression = expressionStatement.getExpression();
        if (expression instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
          final PsiExpression rhs = assignmentExpression.getRExpression();
          final PsiExpression lhs = assignmentExpression.getLExpression();
          final IElementType tokenType = assignmentExpression.getOperationTokenType();
          if (location.equals(lhs) && JavaTokenType.EQ == tokenType && !VariableAccessUtils.variableIsUsed(variable, rhs)) {
            PsiDeclarationStatement newDeclaration = createNewDeclaration(variable, rhs, tracker);
            newDeclaration = (PsiDeclarationStatement)statementParent.addBefore(newDeclaration, statement);
            final PsiElement parent = assignmentExpression.getParent();
            assert parent != null;
            tracker.delete(parent);
            return newDeclaration;
          }
        }
      }
      PsiDeclarationStatement newDeclaration = createNewDeclaration(variable, initializer, tracker);
      if (statement instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)statement;
        final PsiStatement initialization = forStatement.getInitialization();
        newDeclaration = (PsiDeclarationStatement)forStatement.addBefore(newDeclaration, initialization);
        if (initialization != null) {
          tracker.delete(initialization);
        }
        return newDeclaration;
      }
      else {
        return (PsiDeclarationStatement)statementParent.addBefore(newDeclaration, statement);
      }
    }
  }
}
