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
import com.intellij.psi.PsiType;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.InstanceOfInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

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

        final DFAType type = getInferredType(refExpr.getReferenceName(), instruction, flow, scope, new HashSet<MixinTypeInstruction>());
        if (type == null) return null;
        return type.getResultType();
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

    final DFAType type = getInferredType(variableName, instruction, flow, scope, new HashSet<MixinTypeInstruction>());
    return type != null ? type.getResultType() : null;
  }

  public static boolean isTooComplexTooAnalyze(GrControlFlowOwner scope) {
    return getDefUseMaps(scope) == null;
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
  private static DFAType getInferredType(@NotNull String varName, @NotNull Instruction instruction, @NotNull Instruction[] flow, @NotNull GrControlFlowOwner scope, Set<MixinTypeInstruction> trace) {
    final Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> pair = getDefUseMaps(scope);

    List<DefinitionMap> dfaResult = pair.second;
    if (dfaResult == null) return null;

    final int varIndex = pair.first.getVarIndex(varName);

    final DefinitionMap allDefs = dfaResult.get(instruction.num());
    final int[] varDefs = allDefs.getDefinitions(varIndex);
    if (varDefs == null) return null;

    DFAType result = null;
    for (int defIndex : varDefs) {
      DFAType defType = getDefinitionType(flow[defIndex], flow, scope, trace);

      if (defType != null) {
        defType = defType.negate(instruction);
      }

      if (defType != null) {
        result = result == null ? defType : DFAType.create(defType, result, scope.getManager());
      }
    }
    return result;
  }

  private static Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> getDefUseMaps(final GrControlFlowOwner scope) {
    return CachedValuesManager.getManager(scope.getProject()).getCachedValue(scope, new CachedValueProvider<Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>>>() {
      @Override
      public Result<Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>>> compute() {
        final Instruction[] flow = scope.getControlFlow();
        final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow) {
          @Override
          public void fun(DefinitionMap m, Instruction instruction) {
            if (instruction instanceof InstanceOfInstruction) {
              final InstanceOfInstruction instanceOfInstruction = (InstanceOfInstruction)instruction;
              ReadWriteVariableInstruction i = instanceOfInstruction.getInstructionToMixin(flow);
              if (i != null) {
                int varIndex = getVarIndex(i.getVariableName());
                if (varIndex >= 0) {
                  m.registerDef(instruction, varIndex);
                }
              }
            }
            else if (instruction instanceof ArgumentInstruction) {
              final int varIndex = getVarIndex(((ArgumentInstruction)instruction).getVariableName());
              m.registerDef(instruction, varIndex);
            }
            else {
              super.fun(m, instruction);
            }
          }
        };
        final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
        final DFAEngine<DefinitionMap> engine = new DFAEngine<DefinitionMap>(flow, dfaInstance, lattice);
        final List<DefinitionMap> dfaResult = engine.performDFAWithTimeout();
        return Result.create(Pair.create(dfaInstance, dfaResult), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static DFAType getDefinitionType(Instruction instruction, Instruction[] flow, GrControlFlowOwner scope, Set<MixinTypeInstruction> trace) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        return DFAType.create(TypesUtil.boxPrimitiveType(getInitializerType(element), scope.getManager(), scope.getResolveScope()));
      }
    }
    if (instruction instanceof MixinTypeInstruction) {
      return mixinType((MixinTypeInstruction)instruction, flow, scope, trace);
    }
    return null;
  }

  @Nullable
  private static DFAType mixinType(final MixinTypeInstruction instruction, final Instruction[] flow, final GrControlFlowOwner scope, Set<MixinTypeInstruction> trace) {
    if (!trace.add(instruction)) {
      return null;
    }

    String varName = instruction.getVariableName();
    if (varName == null) {
      return null;
    }
    ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(flow);
    if (originalInstr == null) {
      LOG.error(scope.getContainingFile().getName() + ":" + scope.getText());
    }

    DFAType original = getInferredType(varName, originalInstr, flow, scope, trace);
    final PsiType mixin = instruction.inferMixinType();
    if (mixin == null) {
      return original;
    }
    if (original == null) {
      original = DFAType.create(null);
    }
    original.addMixin(mixin, instruction.getConditionInstruction());
    trace.remove(instruction);
    return original;
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

  /*@Nullable
  private static PsiType getInferredType(@NotNull String varName,
                                         @NotNull Instruction instruction,
                                         @NotNull GrControlFlowOwner scope) {
    final ArrayList<Map<String, DFAType>> dfaResult = getDefUseMaps(scope);

    if (dfaResult == null) return null;


    final Map<String, DFAType> allDefs = dfaResult.get(instruction.num());
    final DFAType dfaType = allDefs.get(varName);
    if (dfaType == null) return null;

    return dfaType.getType();
  }

  private static ArrayList<Map<String, DFAType>> getDefUseMaps(final GrControlFlowOwner scope) {
    return CachedValuesManager.getManager(scope.getProject()).getCachedValue(scope, new CachedValueProvider<ArrayList<Map<String, DFAType>>>() {
      @Override
      public Result<ArrayList<Map<String, DFAType>>> compute() {
        final Instruction[] flow = scope.getControlFlow();
        final DFAEngine<Map<String, DFAType>> engine = new DFAEngine<Map<String, DFAType>>(flow, new TypeDFAInstance(), new TypesSemilattice(scope.getManager()));
        final ArrayList<Map<String, DFAType>> result = engine.performDFAWithTimeout();
        return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }*/
}
