/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AnonymousClassVariableHidesOuterClassVariableVisitor extends BaseInspectionVisitor {

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    super.visitAnonymousClass(aClass);
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(aClass, PsiCodeBlock.class);
    if (codeBlock == null) {
      return;
    }
    final VariableCollector collector = new VariableCollector();
    aClass.acceptChildren(collector);
    final PsiStatement[] statements = codeBlock.getStatements();
    final int offset = aClass.getTextOffset();
    for (PsiStatement statement : statements) {
      if (statement.getTextOffset() >= offset) {
        break;
      }
      if (!(statement instanceof PsiDeclarationStatement)) {
        continue;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          continue;
        }
        final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElement;
        final String name = localVariable.getName();
        final PsiVariable[] variables = collector.getVariables(name);
        for (PsiVariable variable : variables) {
          registerVariableError(variable, variable);
        }
      }
    }
    final PsiElement containingMethod = PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class, PsiLambdaExpression.class);
    if (containingMethod == null) {
      return;
    }

    final PsiParameterList parameterList = containingMethod instanceof PsiMethod ? ((PsiMethod)containingMethod).getParameterList() 
                                                                                 : ((PsiLambdaExpression)containingMethod).getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final String name = parameter.getName();
      final PsiVariable[] variables = collector.getVariables(name);
      for (PsiVariable variable : variables) {
        registerVariableError(variable, variable);
      }
    }
  }

  private static class VariableCollector extends JavaRecursiveElementWalkingVisitor {

    private static final PsiVariable[] EMPTY_VARIABLE_LIST = {};

    private final Map<String, List<PsiVariable>> variableMap = new HashMap<>();

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      final String name = variable.getName();
      final List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        final List<PsiVariable> list = new ArrayList<>();
        list.add(variable);
        variableMap.put(name, list);
      }
      else {
        variableList.add(variable);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // don't drill down in classes
    }

    public PsiVariable[] getVariables(String name) {
      final List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        return EMPTY_VARIABLE_LIST;
      }
      else {
        return variableList.toArray(new PsiVariable[variableList.size()]);
      }
    }
  }
}
