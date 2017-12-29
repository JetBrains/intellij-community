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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TooBroadScopeInspection extends TooBroadScopeInspectionBase {
  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return new TooBroadScopeInspectionFix(variable.getName());
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
      final PsiVariable variable = (PsiVariable)variableIdentifier.getParent();
      assert variable != null;
      final PsiElement variableScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class, PsiTryStatement.class);
      final List<PsiReferenceExpression> references = VariableAccessUtils.findReferences(variable, variableScope);
      PsiElement commonParent = ScopeUtils.getCommonParent(references);
      assert commonParent != null;
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
        PsiResourceVariable resourceVariable = createResourceVariable(project, (PsiLocalVariable)variable, initializer != null ? tracker.markUnchanged(initializer) : null);
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

    private PsiResourceVariable createResourceVariable(@NotNull Project project, PsiLocalVariable variable, PsiExpression initializer) {
      PsiTryStatement tryStatement = (PsiTryStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("try (X x = null){}", variable);
      PsiResourceList resourceList = tryStatement.getResourceList();
      assert resourceList != null;
      PsiResourceVariable resourceVariable = (PsiResourceVariable)resourceList.iterator().next();
      resourceVariable.getTypeElement().replace(variable.getTypeElement());
      PsiIdentifier nameIdentifier = resourceVariable.getNameIdentifier();
      assert nameIdentifier != null;
      PsiIdentifier oldIdentifier = variable.getNameIdentifier();
      assert oldIdentifier != null;
      nameIdentifier.replace(oldIdentifier);
      if (initializer != null) {
        resourceVariable.setInitializer(initializer);
      }
      return resourceVariable;
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
      if (isMoveable(initializer) && statement instanceof PsiExpressionStatement) {
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
