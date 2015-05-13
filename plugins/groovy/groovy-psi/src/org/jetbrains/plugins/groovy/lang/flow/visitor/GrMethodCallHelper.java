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
package org.jetbrains.plugins.groovy.lang.flow.visitor;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.MethodCallHelper;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrMethodCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.StandardInstructionVisitor.forceNotNull;
import static org.jetbrains.plugins.groovy.lang.flow.visitor.GrNullabilityProblem.passingNullableArgumentToNonAnnotatedParameter;
import static org.jetbrains.plugins.groovy.lang.flow.visitor.GrNullabilityProblem.passingNullableToNotNullParameter;

public class GrMethodCallHelper<V extends GrGenericStandardInstructionVisitor<V>> extends MethodCallHelper<GrMethodCallInstruction<V>> {

  private final DfaValueFactory myFactory;
  private final GrGenericStandardInstructionVisitor<V> myVisitor;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<GrMethodCallInstruction, Nullness> myReturnTypeNullability =
    new FactoryMap<GrMethodCallInstruction, Nullness>() {
      @Override
      protected Nullness create(GrMethodCallInstruction key) {
        final GrExpression callExpression = key.getCall();
        if (callExpression instanceof GrNewExpression) {
          return Nullness.NOT_NULL;
        }
        return DfaPsiUtil.getElementNullability(key.getReturnType(), key.getTargetMethod());
      }
    };

  public GrMethodCallHelper(GrGenericStandardInstructionVisitor<V> visitor) {
    myVisitor = visitor;
    myFactory = visitor.getFactory();
  }


  public DfaValue getMethodResultValue(GrMethodCallInstruction<V> instruction) {
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    final PsiType type = instruction.getReturnType();

    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      Nullness nullability = myReturnTypeNullability.get(instruction);
      if (nullability == Nullness.UNKNOWN && myFactory.isUnknownMembersAreNullable()) {
        nullability = Nullness.NULLABLE;
      }
      return myFactory.createTypeValue(type, nullability);
    }
    return DfaUnknownValue.getInstance();
  }

  @Override
  protected DfaValue getMethodResultValue(GrMethodCallInstruction<V> instruction, @Nullable DfaValue qualifierValue) {
    return getMethodResultValue(instruction);
  }

  @Override
  protected Producer<PsiType> getProducer(final GrMethodCallInstruction<V> instruction) {
    return new Producer<PsiType>() {
      @Nullable
      @Override
      public PsiType produce() {
        return instruction.getReturnType();
      }
    };
  }

  @Override
  protected DfaValueFactory getFactory() {
    return myFactory;
  }

  @NotNull
  DfaValue[] popAndCheckCallArguments(GrMethodCallInstruction instruction, DfaMemoryState state) {
    final List<DfaValue> result = ContainerUtil.newArrayList();

    final Stack<DfaValue> arguments = ContainerUtil.newStack();
    for (final GrClosableBlock ignored : instruction.getClosureArguments()) {
      arguments.push(state.pop());
    }
    for (final GrExpression ignored : instruction.getExpressionArguments()) {
      arguments.push(state.pop());
    }
    if (instruction.getNamedArguments().length > 0) {
      result.add(state.pop());
    }

    for (final GrExpression expression : instruction.getExpressionArguments()) {
      final DfaValue arg = arguments.pop();
      final Nullness nullability = instruction.getParameterNullability(expression);
      if (nullability == Nullness.NOT_NULL) {
        if (!myVisitor.checkNotNullable(state, arg, passingNullableToNotNullParameter, expression)) {
          forceNotNull(getFactory(), state, arg);
        }
      }
      else if (nullability == Nullness.UNKNOWN) {
        myVisitor.checkNotNullable(state, arg, passingNullableArgumentToNonAnnotatedParameter, expression);
      }
      result.add(arg);
    }

    for (final GrClosableBlock ignored : instruction.getClosureArguments()) {
      result.add(arguments.pop());
    }
    return result.toArray(new DfaValue[result.size()]);
  }
}
