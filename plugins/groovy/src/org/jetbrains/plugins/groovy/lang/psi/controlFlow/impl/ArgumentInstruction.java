/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
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

import static org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.createSignature;

/**
 * @author Max Medvedev
 */
public class ArgumentInstruction extends InstructionImpl implements MixinTypeInstruction {
  private static final Logger LOG = Logger.getInstance(ArgumentInstruction.class);

  public ArgumentInstruction(@Nullable GrReferenceExpression ref) {
    super(ref);
  }

  @Nullable
  public PsiType inferMixinType() {
    PsiElement element = getElement();
    GrCall call = findCall(element);
    GrExpression[] arguments = call.getExpressionArguments();
    boolean hasNamed = call.getNamedArguments().length > 0;

    GroovyResolveResult[] variants = getCallVariantsWithoutUsingArgumentTypes(call, arguments);
    if (variants.length == 0) return null;

    int index = ArrayUtil.indexOf(arguments, element) + (hasNamed ? 1 : 0);
    return findParameterTypeIfSameInAllOverloads(hasNamed, variants, index);
  }

  @Nullable
  private static PsiType findParameterTypeIfSameInAllOverloads(boolean hasNamed, GroovyResolveResult[] variants, int index) {
    PsiType result = findParameterTypeUnambiguously(index, hasNamed, variants[0]);
    if (result == null) return null;

    for (int i = 1; i < variants.length; i++) {
      GroovyResolveResult variant = variants[i];
      if (!result.equals(findParameterTypeUnambiguously(index, hasNamed, variant))) {
        return null;
      }
    }
    return result;
  }

  private static GroovyResolveResult[] getCallVariantsWithoutUsingArgumentTypes(GrCall call, GrExpression[] arguments) {
    // we should be careful so that resolve doesn't use the type of the arguments at all,
    // as we're right now calculating at least one of them
    GrExpression firstArg = arguments.length == 0 ? null : arguments[0];
    return call.getCallVariants(firstArg);
  }

  private static GrCall findCall(PsiElement element) {
    PsiElement parent = element.getParent().getParent();
    LOG.assertTrue(parent instanceof GrCall, "elemText: " + element.getText() +
                                             "\nParent = " + (element.getParent() == null ? "null" : element.getParent().getClass()) +
                                             "\nPParent" + (parent == null ? "null" : parent.getClass()));

    return (GrCall)parent;
  }

  @Nullable
  private static PsiType findParameterTypeUnambiguously(int index, boolean hasNamed, GroovyResolveResult variant) {
    GrClosureSignature signature = createSignature(variant);
    if (signature == null || signature.getParameterCount() <= index) return null;

    GrClosureParameter[] parameters = signature.getParameters();
    if (hasNamed && !InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) return null;

    for (int i = 0; i <= index; i++) {
      if (parameters[i].isOptional()) {
        return null;
      }
    }

    PsiType result = parameters[index].getType();
    return result == null || dependsOnTypeParameters(result) ? null : result;
  }

  private static Boolean dependsOnTypeParameters(PsiType result) {
    return result.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        if (classType.resolve() instanceof PsiTypeParameter) {
          return true;
        }
        for (PsiType type : classType.getParameters()) {
          if (type.accept(this)) {
            return true;
          }
        }
        return false;
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Nullable
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        PsiType bound = wildcardType.getBound();
        return bound != null && bound.accept(this);
      }

      @Nullable
      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }
    });
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

