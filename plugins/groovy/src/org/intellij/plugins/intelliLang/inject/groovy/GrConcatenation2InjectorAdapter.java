// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.JavaConcatenationInjectorManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrConcatenation2InjectorAdapter extends JavaConcatenationInjectorManager.BaseConcatenation2InjectorAdapter implements MultiHostInjector {
  public GrConcatenation2InjectorAdapter(JavaConcatenationInjectorManager manager) {
    super(manager);
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return LITERALS;
  }

  @Override
  protected Pair<PsiElement,PsiElement[]> computeAnchorAndOperands(@NotNull PsiElement context) {
    PsiElement element = context;
    PsiElement parent = context.getParent();
    while (parent instanceof GrBinaryExpression && ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mPLUS
           || parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getOperationToken() == GroovyTokenTypes.mPLUS_ASSIGN
           || parent instanceof GrConditionalExpression && ((GrConditionalExpression)parent).getCondition() != element
           || parent instanceof GrTypeCastExpression
           || parent instanceof GrSafeCastExpression
           || parent instanceof GrParenthesizedExpression
           || parent instanceof GrString) {
      element = parent;
      parent = parent.getParent();
    }

    PsiElement[] operands;
    PsiElement anchor;
    if (element instanceof GrBinaryExpression) {
      operands = collectBinaryOperands(((GrBinaryExpression)element));
      anchor = element;
    }
    else if (element instanceof GrString) {
      operands = collectGStringOperands((GrString)element);
      anchor = element;
    }
    else if (element instanceof GrAssignmentExpression) {
      GrExpression rvalue = ((GrAssignmentExpression)element).getRValue();
      operands = new PsiElement[]{rvalue == null ? element : rvalue};
      anchor = element;
    }
    else if (element instanceof GrLiteral && ((GrLiteral)element).isValidHost()) {
      operands = new PsiElement[]{context};
      anchor = context;
    }
    else {
      return Pair.create(context, PsiElement.EMPTY_ARRAY);
    }

    return Pair.create(anchor, operands);
  }

  private static PsiElement[] collectGStringOperands(GrString grString) {
    final ArrayList<PsiElement> operands = ContainerUtil.newArrayList();
    processGString(grString, operands);
    return operands.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static void processGString(GrString string, ArrayList<PsiElement> operands) {
    ContainerUtil.addAll(operands, string.getAllContentParts());
  }

  private static PsiElement[] collectBinaryOperands(GrBinaryExpression expression) {
    final ArrayList<PsiElement> operands = ContainerUtil.newArrayList();
    processBinary(expression, operands);
    return operands.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static void processBinary(GrBinaryExpression expression, ArrayList<PsiElement> operands) {
    final GrExpression left = expression.getLeftOperand();
    final GrExpression right = expression.getRightOperand();
    if (left instanceof GrBinaryExpression) {
      processBinary((GrBinaryExpression)left, operands);
    }
    else if (left instanceof GrString) {
      processGString((GrString)left, operands);
    }
    else {
      operands.add(left);
    }

    if (right instanceof GrBinaryExpression) {
      processBinary((GrBinaryExpression)right, operands);
    }
    else if (right instanceof GrString) {
      processGString((GrString)right, operands);
    }
    else if (right != null) {
      operands.add(right);
    }
  }

  private static final List<Class<? extends GrLiteralContainer>> LITERALS = Arrays.asList(GrLiteral.class, GrStringContent.class);
}
