/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.*;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.AssertionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author ven
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class TypeInferenceHelper {

  private static final Logger LOG = Logger.getInstance(TypeInferenceHelper.class);

  @Nullable
  public static PsiType getInferredType(@NotNull final GrReferenceExpression refExpr) {
    return RecursionManager.doPreventingRecursion(refExpr, true, new NullableComputable<PsiType>() {
      @Override
      public PsiType compute() {
        final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(refExpr);
        if (scope == null) return null;

        final Instruction[] flow = scope.getControlFlow();
        ReadWriteVariableInstruction instruction = findInstruction(refExpr, flow);
        if (instruction == null) return null;

        if (instruction.isWrite()) {
          return getInitializerType(refExpr);
        }

        return getInferredType(refExpr.getReferenceName(), instruction, flow, scope);
      }

    });
  }

  @Nullable
  public static PsiType getInferredType(@NotNull PsiElement place, String variableName) {
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(place);
    if (scope == null) return null;

    final Instruction[] flow = scope.getControlFlow();
    Instruction instruction = findInstructionAt(place, flow);
    if (instruction == null) return null;

    return getInferredType(variableName, instruction, flow, scope);
  }

  public static boolean isTooComplexTooAnalyze(GrControlFlowOwner scope) {
    return getDefUseMaps(scope).second == null;
  }

  @Nullable
  private static Instruction findInstructionAt(PsiElement place, Instruction[] flow) {
    List<Instruction> applicable = new ArrayList<Instruction>();
    for (Instruction instruction : flow) {
      final PsiElement element = instruction.getElement();
      if (element == null) continue;

      if (element == place) return instruction;

      if (PsiTreeUtil.isAncestor(element, place, true)) {
        applicable.add(instruction);
      }
    }
    if (applicable.size() == 0) return null;

    Collections.sort(applicable, new Comparator<Instruction>() {
      @Override
      public int compare(Instruction o1, Instruction o2) {
        PsiElement e1 = o1.getElement();
        PsiElement e2 = o2.getElement();
        LOG.assertTrue(e1 != null);
        LOG.assertTrue(e2 != null);
        final TextRange t1 = e1.getTextRange();
        final TextRange t2 = e2.getTextRange();
        final int s1 = t1.getStartOffset();
        final int s2 = t2.getStartOffset();

        if (s1 == s2) {
          return t1.getEndOffset() - t2.getEndOffset();
        }
        return s2 - s1;
      }
    });

    return applicable.get(0);
  }

  @Nullable
  private static PsiType getInferredType(@NotNull String varName, @NotNull Instruction instruction, @NotNull Instruction[] flow, @NotNull GrControlFlowOwner scope) {
    final Pair<ReachingDefinitionsDfaInstance, List<TIntObjectHashMap<TIntHashSet>>> pair = getDefUseMaps(scope);

    List<TIntObjectHashMap<TIntHashSet>> dfaResult = pair.second;
    if (dfaResult == null) return null;

    final int varIndex = pair.first.getVarIndex(varName);

    final TIntObjectHashMap<TIntHashSet> allDefs = dfaResult.get(instruction.num());
    final TIntHashSet varDefs = allDefs.get(varIndex);
    if (varDefs == null) return null;

    PsiType result = null;
    for (int defIndex : varDefs.toArray()) {
      PsiType defType = getDefinitionType(flow[defIndex], flow, scope);
      if (defType != null) {
        defType = TypesUtil.boxPrimitiveType(defType, scope.getManager(), scope.getResolveScope());
        result = result == null ? defType : TypesUtil.getLeastUpperBound(result, defType, scope.getManager());
      }
    }
    return result;
  }

  private static Pair<ReachingDefinitionsDfaInstance, List<TIntObjectHashMap<TIntHashSet>>> getDefUseMaps(final GrControlFlowOwner scope) {
    return CachedValuesManager.getManager(scope.getProject()).getCachedValue(scope, new CachedValueProvider<Pair<ReachingDefinitionsDfaInstance, List<TIntObjectHashMap<TIntHashSet>>>>() {
      @Override
      public Result<Pair<ReachingDefinitionsDfaInstance, List<TIntObjectHashMap<TIntHashSet>>>> compute() {
        final Instruction[] flow = scope.getControlFlow();
        final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow) {
          @Override
          public void fun(TIntObjectHashMap<TIntHashSet> m, Instruction instruction) {
            if (instruction instanceof AssertionInstruction) { //todo assertions are not defs, they just add to type intersection and don't overwrite it completely
              final AssertionInstruction assertionInstruction = (AssertionInstruction)instruction;
              final PsiElement element = assertionInstruction.getElement();
              if (element instanceof GrInstanceOfExpression && !assertionInstruction.isNegate()) {
                final GrExpression operand = ((GrInstanceOfExpression)element).getOperand();
                final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
                if (typeElement != null) {
                  final int varIndex = getVarIndex(operand.getText());
                  if (varIndex >= 0) {
                    registerDef(m, instruction, varIndex);
                  }
                }
              }
            }
            else if (instruction instanceof ArgumentInstruction) {
              final int varIndex = getVarIndex(((ArgumentInstruction)instruction).getVariableName());
              registerDef(m, instruction, varIndex);
            }
            else {
              super.fun(m, instruction);
            }
          }
        };
        final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
        final DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
        final List<TIntObjectHashMap<TIntHashSet>> dfaResult = engine.performDFAWithTimeout();
        return Result.create(Pair.create(dfaInstance, dfaResult), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static PsiType getDefinitionType(Instruction instruction, Instruction[] flow, GrControlFlowOwner scope) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        return getInitializerType(element);
      }
    }
    if (instruction instanceof MixinTypeInstruction) {
      return mixinType((MixinTypeInstruction)instruction, flow, scope);
    }
    return null;
  }

  @Nullable
  private static PsiType mixinType(final MixinTypeInstruction instruction, final Instruction[] flow, final GrControlFlowOwner scope) {
    return RecursionManager.doPreventingRecursion(instruction, false, new NullableComputable<PsiType>() {
      @Override
      @Nullable
      public PsiType compute() {
        String varName = instruction.getVariableName();
        LOG.assertTrue(varName != null, scope.getText());
        ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(flow);
        LOG.assertTrue(originalInstr != null, scope.getText());
        final PsiType original = getInferredType(varName, originalInstr, flow, scope);
        final PsiType mixin = instruction.inferMixinType();
        if (mixin == null) return original;
        if (original == null) return mixin;
        if (TypesUtil.isAssignableByMethodCallConversion(mixin, original, scope)) return original;
        return PsiIntersectionType.createIntersection(mixin, original);
      }
    });
  }


  @Nullable
  private static ReadWriteVariableInstruction findInstruction(final GrReferenceExpression refExpr, final Instruction[] flow) {
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction && instruction.getElement() == refExpr) {
        return (ReadWriteVariableInstruction)instruction;
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getInitializerType(final PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null) {
      return getInitializerFor(element);
    }

    if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable)element;
        if (!(variable instanceof GrParameter)) {
          final GrExpression initializer = variable.getInitializerGroovy();
          if (initializer != null) {
            return initializer.getType();
          }
        }
        return variable.getTypeGroovy();
    }

    return null;
  }

  @Nullable
  public static PsiType getInitializerFor(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof GrAssignmentExpression) {
      return ((GrAssignmentExpression)parent).getType();
    }

    if (parent instanceof GrTupleExpression) {
      GrTupleExpression list = (GrTupleExpression)parent;
      if (list.getParent() instanceof GrAssignmentExpression) { // multiple assignment
        final GrExpression rValue = ((GrAssignmentExpression) list.getParent()).getRValue();
        int idx = list.indexOf(element);
        if (idx >= 0 && rValue != null) {
          PsiType rType = rValue.getType();
          if (rType instanceof GrTupleType) {
            PsiType[] componentTypes = ((GrTupleType) rType).getComponentTypes();
            if (idx < componentTypes.length) return componentTypes[idx];
            return null;
          }
          return PsiUtil.extractIterableTypeParameter(rType, false);
        }
      }
    }
    if (parent instanceof GrUnaryExpression &&
        TokenSets.POSTFIX_UNARY_OP_SET.contains(((GrUnaryExpression)parent).getOperationTokenType())) {
      return ((GrUnaryExpression)parent).getType();
    }

    return null;
  }
}
