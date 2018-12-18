// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.impl.ArgumentsKt.argumentMapping;
import static org.jetbrains.plugins.groovy.lang.resolve.impl.ArgumentsKt.getArguments;

/**
 * @author Max Medvedev
 */
public class ArgumentInstruction extends InstructionImpl implements MixinTypeInstruction {
  private static final Logger LOG = Logger.getInstance(ArgumentInstruction.class);

  public ArgumentInstruction(@NotNull GrReferenceExpression ref) {
    super(ref);
  }

  @Override
  @Nullable
  public PsiType inferMixinType() {
    final GrReferenceExpression expression = (GrReferenceExpression)getElement();
    assert expression != null;
    final GrCall call = findCall(expression);

    final List<Argument> arguments = getArguments(call);
    if (arguments == null) return null;

    PsiType result = null;
    for (GroovyResolveResult variant : call.multiResolve(true)) {
      if (variant.isInvokedOnProperty()) continue;
      final PsiElement element = variant.getElement();
      if (!(element instanceof PsiMethod)) continue;
      ArgumentMapping mapping = argumentMapping((PsiMethod)element, variant.getSubstitutor(), arguments, call);
      PsiType parameterType = mapping.expectedType(new ExpressionArgument(expression));
      result = TypesUtil.getLeastUpperBoundNullable(result, parameterType, expression.getManager());
    }
    return result;
  }

  private static GrCall findCall(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    PsiElement pParent = parent == null ? null : parent.getParent();
    if (!(pParent instanceof GrCall)) {
      LOG.error("elemText: " + element.getText() +
                "\nisValid = " + element.isValid() +
                "\nParent = " + (parent == null ? "null" : parent.getClass()) +
                "\nPParent = " + (pParent == null ? "null" : pParent.getClass()));
    }
    return (GrCall)pParent;
  }

  @Nullable
  @Override
  public ReadWriteVariableInstruction getInstructionToMixin(Instruction[] flow) {
    Instruction instruction = ControlFlowUtils.findInstruction(getElement(), flow);
    if (instruction instanceof ReadWriteVariableInstruction) {
      return (ReadWriteVariableInstruction)instruction;
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public String getVariableName() {
    //noinspection ConstantConditions
    return ((GrReferenceExpression)getElement()).getReferenceName();
  }

  @Nullable
  @Override
  public ConditionInstruction getConditionInstruction() {
    return null;
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return "ARGUMENT " + super.getElementPresentation();
  }
}

