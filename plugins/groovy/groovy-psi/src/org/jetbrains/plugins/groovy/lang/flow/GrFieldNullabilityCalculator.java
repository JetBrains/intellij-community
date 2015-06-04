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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrMethodCallInstruction;
import org.jetbrains.plugins.groovy.lang.flow.visitor.GrStandardInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInspection.dataFlow.Nullness.*;
import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

public class GrFieldNullabilityCalculator extends FieldNullabilityCalculator {

  @Nullable
  @Override
  public Nullness calculate(@NotNull PsiField field) {
    final PsiClass aClass = field.getContainingClass();
    if (!(aClass instanceof GrTypeDefinition) || !(field instanceof GrField)) {
      return UNKNOWN;
    }
    final GrField grField = (GrField)field;
    final GrTypeDefinition typeDefinition = (GrTypeDefinition)aClass;
    return calculateInner(typeDefinition).get(grField);
  }

  @NotNull
  private static Map<GrField, Nullness> calculateInner(final @NotNull GrTypeDefinition typeDefinition) {
    final GrField[] finalFields = getFinalFields(typeDefinition);
    if (finalFields.length == 0) return Collections.emptyMap();
    final PsiMethod[] constructors = typeDefinition.getConstructors();
    if (constructors.length == 0) return Collections.emptyMap();

    final Map<GrField, Nullness> result = new NullabilityMap();
    for (PsiMethod constructor : constructors) {
      if (constructor instanceof GrMethod) {
        result.putAll(calculateForConstructor(typeDefinition, (GrMethod)constructor));
      }
    }
    return Collections.unmodifiableMap(result);
  }

  @NotNull
  private static GrField[] getFinalFields(@NotNull final GrTypeDefinition typeDefinition) {
    return CachedValuesManager.getCachedValue(typeDefinition, new CachedValueProvider<GrField[]>() {
      @Nullable
      @Override
      public Result<GrField[]> compute() {
        final List<GrField> finalFields = ContainerUtil.newArrayList();
        for (GrField field : typeDefinition.getFields()) {
          if (field.hasModifierProperty(PsiModifier.FINAL)) {
            finalFields.add(field);
          }
        }
        return Result.create(
          finalFields.toArray(new GrField[finalFields.size()]),
          typeDefinition, MODIFICATION_COUNT
        );
      }
    });
  }

  @NotNull
  private static Map<GrField, Nullness> calculateForConstructor(@NotNull final GrTypeDefinition typeDefinition,
                                                                @NotNull final GrMethod constructor) {
    return CachedValuesManager.getCachedValue(constructor, new CachedValueProvider<Map<GrField, Nullness>>() {
      @Nullable
      @Override
      public Result<Map<GrField, Nullness>> compute() {
        final FieldNullabilityRunner dfaRunner = new FieldNullabilityRunner(typeDefinition);
        final GrStandardInstructionVisitor visitor = new GrStandardInstructionVisitor(dfaRunner);
        final RunnerResult rc = dfaRunner.analyzeMethod(constructor, visitor);
        return Result.create(
          rc == RunnerResult.OK ? Collections.unmodifiableMap(dfaRunner.getResult()) : Collections.<GrField, Nullness>emptyMap(),
          constructor, MODIFICATION_COUNT
        );
      }
    });
  }


  private static class FieldNullabilityRunner extends GrDataFlowRunner {

    private final Map<GrField, Nullness> myResult = new NullabilityMap();
    private final @NotNull GrTypeDefinition myContainingClass;

    public FieldNullabilityRunner(@NotNull GrTypeDefinition containingClass) {
      super(false, false);
      myContainingClass = containingClass;
    }

    @NotNull
    private Map<GrField, Nullness> getResult() {
      return myResult;
    }

    @NotNull
    @Override
    protected DfaInstructionState[] acceptInstruction(InstructionVisitor visitor,
                                                      DfaInstructionState instructionState) {
      final Instruction instruction = instructionState.getInstruction();
      if (instruction instanceof GrMethodCallInstruction) {
        // if we run into this(args) constructor invocation, we assume that final field initialized there,
        // so we don't care if there are another instructions in the flow
        // so we can take result for that constructor
        final PsiElement callElement = ((GrMethodCallInstruction)instruction).getCall();
        if (callElement instanceof GrConstructorInvocation && ((GrConstructorInvocation)callElement).isThisCall()) {
          final PsiMethod method = ((GrConstructorInvocation)callElement).resolveMethod();
          if (method instanceof GrMethod && method.isConstructor()) {
            myResult.clear();
            myResult.putAll(calculateForConstructor(myContainingClass, (GrMethod)method));
            return DfaInstructionState.EMPTY_ARRAY;
          }
        }
      }
      // ok, end of flow, collect the results
      else if (instruction instanceof ReturnInstruction && !((ReturnInstruction)instruction).isViaException()) {
        for (GrField field : getFinalFields(myContainingClass)) {
          final DfaVariableValue variableValue = getFactory().getVarFactory().createVariableValue(field, false);
          if (instructionState.getMemoryState().isNotNull(variableValue)) {
            myResult.put(field, NOT_NULL);
          }
          else if (instructionState.getMemoryState().isNull(variableValue)) {
            myResult.put(field, NULLABLE);
          }
          else {
            myResult.put(field, UNKNOWN);
          }
        }
        return DfaInstructionState.EMPTY_ARRAY;
      }
      return super.acceptInstruction(visitor, instructionState);
    }
  }

  private static class NullabilityMap extends HashMap<GrField, Nullness> {

    // NOT_NULL < UNKNOWN < NULLABLE
    @Override
    public Nullness put(GrField key, Nullness value) {
      final Nullness existing = get(key);
      if (existing == value) {
        return existing;
      }
      else if (existing == null || existing == NOT_NULL || value == NULLABLE) {
        return super.put(key, value);
      }
      else {
        return existing;
      }
    }
  }
}
