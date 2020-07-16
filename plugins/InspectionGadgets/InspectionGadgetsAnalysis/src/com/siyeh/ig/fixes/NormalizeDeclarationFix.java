/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class NormalizeDeclarationFix extends InspectionGadgetsFix {

  private final boolean myCStyleDeclaration;

  public NormalizeDeclarationFix(boolean cStyleDeclaration) {
    myCStyleDeclaration = cStyleDeclaration;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myCStyleDeclaration
           ? InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix")
           : InspectionGadgetsBundle.message("normalize.declaration.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiVariable) && !(element instanceof PsiMethod) && !(element instanceof PsiDeclarationStatement)) {
      element = element.getParent();
    }
    if (element instanceof PsiParameter || element instanceof PsiRecordComponent) {
      JavaSharedImplUtil.normalizeBrackets((PsiVariable)element);
      return;
    }
    if (element instanceof PsiLocalVariable) {
      element = element.getParent();
      if (!(element instanceof PsiDeclarationStatement)) {
        return;
      }
    }
    if (element instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)element;
      final PsiElement grandParent = element.getParent();
      if (grandParent instanceof PsiForStatement) {
        splitMultipleDeclarationInForStatementInitialization((PsiForStatement)grandParent);
      }
      else {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        final PsiVariable variable = (PsiVariable)elements[0];
        variable.normalizeDeclaration();
        for (int i = 1; i < elements.length; i++) {
          declarationStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiDeclarationStatement.class);
          assert declarationStatement != null;
          JavaSharedImplUtil.normalizeBrackets((PsiVariable)declarationStatement.getDeclaredElements()[0]);
        }
      }
    }
    else if (element instanceof PsiField) {
      PsiField field = DeclarationSearchUtils.findFirstFieldInDeclaration((PsiField)element);
      PsiField nextField = field;
      int count = 0;
      while (nextField != null) {
        count++;
        nextField = DeclarationSearchUtils.findNextFieldInDeclaration(nextField);
      }
      assert field != null;
      field.normalizeDeclaration();
      for (int i = 1; i < count; i++) {
        field = PsiTreeUtil.getNextSiblingOfType(field, PsiField.class);
        assert field != null;
        JavaSharedImplUtil.normalizeBrackets(field);
      }
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      PsiElement child = method.getParameterList();
      List<PsiElement> psiAnnotationsToDelete = new SmartList<>();
      while (child != null && !(child instanceof PsiCodeBlock)) {
        final PsiElement elementToDelete = child;
        child = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
        if (elementToDelete instanceof PsiJavaToken) {
          final IElementType tokenType = ((PsiJavaToken)elementToDelete).getTokenType();
          if (JavaTokenType.LBRACKET.equals(tokenType) || JavaTokenType.RBRACKET.equals(tokenType)) {
            elementToDelete.delete();
          }
        }
        else if (elementToDelete instanceof PsiAnnotation) {
          psiAnnotationsToDelete.add(elementToDelete);
        }
      }
      final PsiTypeElement typeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(returnType);
      returnTypeElement.replace(typeElement);
      psiAnnotationsToDelete.forEach(PsiElement::delete);
    }
  }

  private static void splitMultipleDeclarationInForStatementInitialization(PsiForStatement forStatement) {
    if (!(forStatement.getParent() instanceof PsiCodeBlock)) {
      forStatement = BlockUtils.expandSingleStatementToBlockStatement(forStatement);
    }
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return;
    }
    final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)initialization;
    final List<PsiLocalVariable> variables =
      StreamEx.of(declarationStatement.getDeclaredElements()).select(PsiLocalVariable.class).toList();
    final int min, max;
    final boolean dependentVariables = containsDependentVariables(variables);
    if (dependentVariables) {
      min = 0;
      max = variables.size() - 1;
    }
    else {
      min = 1;
      max = variables.size();
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(forStatement.getProject());

    final CommentTracker ct = new CommentTracker();
    for (int i = min; i < max; i++) {
      final PsiVariable variable = variables.get(i);
      final String name = variable.getName();
      assert name != null;
      final PsiDeclarationStatement newStatement =
        factory.createVariableDeclarationStatement(name, variable.getType(), ct.markUnchanged(variable.getInitializer()),
                                                   declarationStatement);
      forStatement.getParent().addBefore(newStatement, forStatement);
    }

    final PsiVariable remainingVariable = variables.get(dependentVariables ? variables.size() - 1 : 0);
    final String name = remainingVariable.getName();
    assert name != null;
    final PsiStatement replacementStatement =
      factory.createVariableDeclarationStatement(name, remainingVariable.getType(), ct.markUnchanged(remainingVariable.getInitializer()),
                                                 declarationStatement);
    ct.replaceAndRestoreComments(declarationStatement, replacementStatement);
  }

  private static boolean containsDependentVariables(List<PsiLocalVariable> variables) {
    if (variables.isEmpty()) return false;
    final Set<PsiLocalVariable> visited = ContainerUtil.newHashSet(variables.get(0));
    for (int i = 1; i < variables.size(); i++) {
      final PsiLocalVariable variable = variables.get(i);
      if (!PsiTreeUtil.processElements(variable.getInitializer(),
                                       element -> !visited.contains(tryResolveLocalVariable(element)))) {
        return true;
      }
      visited.add(variable);
    }
    return false;
  }

  private static PsiLocalVariable tryResolveLocalVariable(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      if (referenceExpression.getQualifierExpression() == null) {
        return ObjectUtils.tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
      }
    }
    return null;
  }
}
