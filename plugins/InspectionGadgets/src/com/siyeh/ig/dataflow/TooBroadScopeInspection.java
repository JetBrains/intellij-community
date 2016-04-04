/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
      final Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope());
      final Collection<PsiReference> referenceCollection = query.findAll();
      final PsiElement[] referenceElements = new PsiElement[referenceCollection.size()];
      int index = 0;
      for (PsiReference reference : referenceCollection) {
        final PsiElement referenceElement = reference.getElement();
        referenceElements[index] = referenceElement;
        index++;
      }
      PsiElement commonParent = ScopeUtils.getCommonParent(referenceElements);
      assert commonParent != null;
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        final PsiElement variableScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class);
        assert variableScope != null;
        commonParent = ScopeUtils.moveOutOfLoopsAndClasses(commonParent, variableScope);
        if (commonParent == null) {
          return;
        }
      }
      final PsiElement referenceElement = referenceElements[0];
      final PsiElement firstReferenceScope = PsiTreeUtil.getParentOfType(referenceElement, PsiCodeBlock.class, PsiForStatement.class);
      if (firstReferenceScope == null) {
        return;
      }
      PsiDeclarationStatement newDeclaration;
      if (commonParent instanceof PsiForStatement) {
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
          newDeclaration = createNewDeclaration(variable, rhs);
        }
        else {
          newDeclaration = createNewDeclaration(variable, initializer);
        }
        newDeclaration = (PsiDeclarationStatement)initialization.replace(newDeclaration);
      } else if (firstReferenceScope.equals(commonParent)) {
        newDeclaration = moveDeclarationToLocation(variable, referenceElement);
      }
      else {
        final PsiElement commonParentChild = ScopeUtils.getChildWhichContainsElement(commonParent, referenceElement);
        if (commonParentChild == null) {
          return;
        }
        final PsiElement location = commonParentChild.getPrevSibling();
        newDeclaration = createNewDeclaration(variable, initializer);
        newDeclaration = (PsiDeclarationStatement)commonParent.addAfter(newDeclaration, location);
      }
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      newDeclaration = (PsiDeclarationStatement)codeStyleManager.reformat(newDeclaration);
      removeOldVariable(variable);
      if (isOnTheFly()) {
        HighlightUtils.highlightElement(newDeclaration);
      }
    }

    private void removeOldVariable(@NotNull PsiVariable variable) {
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      if (declaration == null) {
        return;
      }
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length == 1) {
        declaration.delete();
      }
      else {
        variable.delete();
      }
    }

    private PsiDeclarationStatement createNewDeclaration(@NotNull PsiVariable variable, @Nullable PsiExpression initializer) {
      final Project project = variable.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      String name = variable.getName();
      if (name == null) {
        name = "";
      }
      final String comment = getCommentText(variable) + getCommentText(initializer);
      final PsiType type = variable.getType();
      @NonNls final String statementText;
      final String typeText = type.getCanonicalText();
      if (initializer == null) {
        statementText = typeText + ' ' + name + ';' + comment;
      }
      else {
        final String initializerText = initializer.getText();
        statementText = typeText + ' ' + name + '=' + initializerText + ';' + comment;
      }
      final PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)factory.createStatementFromText(statementText, variable);
      final PsiLocalVariable newVariable = (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
      final PsiModifierList newModifierList = newVariable.getModifierList();
      final PsiModifierList modifierList = variable.getModifierList();
      if (newModifierList != null && modifierList != null) {
        // remove final when PsiDeclarationFactory adds one by mistake
        newModifierList.setModifierProperty(PsiModifier.FINAL, variable.hasModifierProperty(PsiModifier.FINAL));
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
          newModifierList.add(annotation);
        }
      }
      return newDeclaration;
    }

    private String getCommentText(PsiElement element) {
      final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiStatement.class, true, PsiMember.class);
      if (parent == null) {
        return "";
      }
      if (parent instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement parentDeclaration = (PsiDeclarationStatement)parent;
        final PsiElement[] declaredElements = parentDeclaration.getDeclaredElements();
        if (declaredElements.length != 1) {
          return "";
        }
      }
      final PsiElement lastChild = parent.getLastChild();
      if (!(lastChild instanceof PsiComment)) {
        return "";
      }
      final PsiElement prevSibling = lastChild.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        return prevSibling.getText() + lastChild.getText();
      }
      return lastChild.getText();
    }

    private PsiDeclarationStatement moveDeclarationToLocation(@NotNull PsiVariable variable, @NotNull PsiElement location) {
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
            PsiDeclarationStatement newDeclaration = createNewDeclaration(variable, rhs);
            newDeclaration = (PsiDeclarationStatement)statementParent.addBefore(newDeclaration, statement);
            final PsiElement parent = assignmentExpression.getParent();
            assert parent != null;
            parent.delete();
            return newDeclaration;
          }
        }
      }
      PsiDeclarationStatement newDeclaration = createNewDeclaration(variable, initializer);
      if (statement instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)statement;
        final PsiStatement initialization = forStatement.getInitialization();
        newDeclaration = (PsiDeclarationStatement)forStatement.addBefore(newDeclaration, initialization);
        if (initialization != null) {
          initialization.delete();
        }
        return newDeclaration;
      }
      else {
        return (PsiDeclarationStatement)statementParent.addBefore(newDeclaration, statement);
      }
    }
  }
}
