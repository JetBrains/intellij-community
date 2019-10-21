// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isThisRef;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isNullLiteral;

/**
 * @author peter
 */
public class InstanceOfInstruction extends InstructionImpl implements MixinTypeInstruction {
  private final ConditionInstruction myCondition;

  public InstanceOfInstruction(@NotNull GrExpression assertion, ConditionInstruction cond) {
    super(assertion);
    myCondition = cond;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return Objects.requireNonNull(super.getElement());
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return "instanceof: " + getElement().getText();
  }

  @Nullable
  private Pair<GrExpression, PsiType> getInstanceof() {
    final PsiElement element = getElement();
    if (element instanceof GrInstanceOfExpression) {
      GrExpression operand = ((GrInstanceOfExpression)element).getOperand();
      final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
      if (operand instanceof GrReferenceExpression) {
        GrExpression qualifier = ((GrReferenceExpression)operand).getQualifier();
        if ((qualifier == null || isThisRef(qualifier)) && typeElement != null) {
          return Pair.create(((GrInstanceOfExpression)element).getOperand(), typeElement.getType());
        }
      }
    }
    else if (element instanceof GrBinaryExpression && ControlFlowBuilderUtil.isInstanceOfBinary((GrBinaryExpression)element)) {
      GrExpression left = ((GrBinaryExpression)element).getLeftOperand();
      GrExpression right = ((GrBinaryExpression)element).getRightOperand();
      if (right == null) return null;
      GroovyResolveResult result = ((GrReferenceExpression)right).advancedResolve();
      final PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass) {
        PsiClassType type = JavaPsiFacade.getElementFactory(element.getProject()).createType((PsiClass)resolved, result.getSubstitutor());
        return new Pair<>(left, type);
      }
    }
    else if (element instanceof GrBinaryExpression) {
      GrExpression left = ((GrBinaryExpression)element).getLeftOperand();
      GrExpression right = ((GrBinaryExpression)element).getRightOperand();
      if (isNullLiteral(right)) {
        return Pair.create(left, PsiType.NULL);
      }
      else if (right != null && isNullLiteral(left)) {
        return Pair.create(right, PsiType.NULL);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public PsiType inferMixinType() {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null) return null;

    return instanceOf.getSecond();
  }

  @Nullable
  @Override
  public ReadWriteVariableInstruction getInstructionToMixin(Instruction[] flow) {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null) return null;

    Instruction instruction = ControlFlowUtils.findInstruction(instanceOf.getFirst(), flow);
    if (instruction instanceof ReadWriteVariableInstruction) {
      return (ReadWriteVariableInstruction)instruction;
    }
    return null;
  }

  @Nullable
  @Override
  public VariableDescriptor getVariableDescriptor() {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null || !(instanceOf.first instanceof GrReferenceExpression)) return null;
    return VariableDescriptorFactory.createDescriptor((GrReferenceExpression)instanceOf.first);
  }

  @Nullable
  @Override
  public ConditionInstruction getConditionInstruction() {
    return myCondition;
  }
}
