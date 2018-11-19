// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

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
    PsiElement element = getElement();
    assert element != null;

    GrCall call = findCall(element);
    GrExpression[] arguments = call.getExpressionArguments();
    boolean hasNamed = PsiImplUtil.hasNamedArguments(call.getArgumentList());

    int index = ArrayUtil.indexOf(arguments, element) + (hasNamed ? 1 : 0);
    GroovyResolveResult[] variants = call.getCallVariants((GrReferenceExpression)element);

    PsiType result = null;
    for (GroovyResolveResult variant : variants) {
      GrSignature signature = GrClosureSignatureUtil.createSignature(variant);
      if (signature == null) continue;

      if (GrClosureSignatureUtil.mapParametersToArguments(signature, call) != null && !haveNullParameters(call)) {
        return null;
      }
      GrClosureParameter[] parameters = signature.getParameters();
      if (index >= parameters.length) continue;

      result = TypesUtil.getLeastUpperBoundNullable(result, parameters[index].getType(), element.getManager());
    }
    return result;
  }

  private static boolean haveNullParameters(GrCall call) {
    for (GrExpression argument : call.getExpressionArguments()) {
      if (argument.getType() == null) return true;
    }
    return false;
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

