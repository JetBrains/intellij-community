/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
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
      GrClosureSignature signature = GrClosureSignatureUtil.createSignature(variant);
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

  private static GrCall findCall(PsiElement element) {
    PsiElement parent = element.getParent().getParent();
    if (!(parent instanceof GrCall)) {
      LOG.error("elemText: " + element.getText() +
                "\nisValid = " + element.isValid() +
                "\nParent = " + (element.getParent() == null ? "null" : element.getParent().getClass()) +
                "\nPParent = " + (parent == null ? "null" : parent.getClass()));
    }
    return (GrCall)parent;
  }

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

  @Override
  public String getVariableName() {
    //noinspection ConstantConditions
    return ((GrReferenceExpression)getElement()).getReferenceName();
  }

  @Override
  public ConditionInstruction getConditionInstruction() {
    return null;
  }

  @Override
  protected String getElementPresentation() {
    return "ARGUMENT " + super.getElementPresentation();
  }
}

