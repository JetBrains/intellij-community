/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.JavaConcatenationInjectorManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrConcatenation2InjectorAdapter extends JavaConcatenationInjectorManager.BaseConcatenation2InjectorAdapter implements MultiHostInjector {
  public GrConcatenation2InjectorAdapter(Project project) {
    super(project);
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return LITERALS;
  }

  protected Pair<PsiElement,PsiElement[]> computeAnchorAndOperands(@NotNull PsiElement context) {
    PsiElement element = context;
    PsiElement parent = context.getParent();
    while (parent instanceof GrBinaryExpression && ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mPLUS
           || parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getOperationToken() == GroovyTokenTypes.mPLUS_ASSIGN
           || parent instanceof GrConditionalExpression && ((GrConditionalExpression)parent).getCondition() != element
           || parent instanceof GrTypeCastExpression
           || parent instanceof GrSafeCastExpression
           || parent instanceof GrParenthesizedExpression) {
      element = parent;
      parent = parent.getParent();
    }

    PsiElement[] operands;
    PsiElement anchor;
    if (element instanceof GrBinaryExpression) {
      operands = collectBinaryOperands(((GrBinaryExpression)element));
      anchor = element;
    }
    else if (element instanceof GrAssignmentExpression) {
      GrExpression rvalue = ((GrAssignmentExpression)element).getRValue();
      operands = new PsiElement[]{rvalue == null ? element : rvalue};
      anchor = element;
    }
    else {
      operands = new PsiElement[]{context};
      anchor = context;
    }

    return Pair.create(anchor, operands);
  }

  private static PsiElement[] collectBinaryOperands(GrBinaryExpression expression) {
    final ArrayList<PsiElement> operands = ContainerUtil.newArrayList();
    processBinary(expression, operands);
    return operands.toArray(new PsiElement[operands.size()]);
  }

  private static void processBinary(GrBinaryExpression expression, ArrayList<PsiElement> operands) {
    final GrExpression left = expression.getLeftOperand();
    final GrExpression right = expression.getRightOperand();
    if (left instanceof GrBinaryExpression) {
      processBinary((GrBinaryExpression)left, operands);
    }
    else {
      operands.add(left);
    }

    if (right instanceof GrBinaryExpression) {
      processBinary((GrBinaryExpression)right, operands);
    }
    else {
      operands.add(right);
    }
  }

  private static final List<Class<GrLiteral>> LITERALS = Arrays.asList(GrLiteral.class);
}
