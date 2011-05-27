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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.*;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.AssertionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.List;

/**
 * @author ven
 */
public class TypeInferenceHelper {

  @Nullable
  public static PsiType getInferredType(final GrReferenceExpression refExpr) {
    return RecursionManager.createGuard("refType").doPreventingRecursion(refExpr, new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        GroovyPsiElement scope =
          PsiTreeUtil.getParentOfType(refExpr, GrMethod.class, GrClosableBlock.class, GrClassInitializer.class, GroovyFileBase.class);
        if (scope instanceof GrMethod) {
          scope = ((GrMethod)scope).getBlock();
        }
        else if (scope instanceof GrClassInitializer) {
          scope = ((GrClassInitializer)scope).getBlock();
        }

        if (scope != null) {
          final Instruction[] flow = ((GrControlFlowOwner)scope).getControlFlow();
          ReadWriteVariableInstruction instruction = findInstruction(refExpr, flow);
          if (instruction == null) {
            return null;
          }
          if (instruction.isWrite()) {
            return getInitializerType(refExpr);
          }

          final Pair<ReachingDefinitionsDfaInstance, List<TIntObjectHashMap<TIntHashSet>>> pair = getDefUseMaps((GrControlFlowOwner)scope);

          final int varIndex = pair.first.getVarIndex(refExpr.getReferenceName());
          final TIntObjectHashMap<TIntHashSet> allDefs = pair.second.get(instruction.num());
          final TIntHashSet varDefs = allDefs.get(varIndex);
          if (varDefs != null) {
            PsiType result = null;
            for (int defIndex : varDefs.toArray()) {
              PsiType defType = getDefinitionType(flow[defIndex]);
              if (defType != null) {
                defType = TypesUtil.boxPrimitiveType(defType, scope.getManager(), scope.getResolveScope());
                result = result == null ? defType : TypesUtil.getLeastUpperBound(result, defType, scope.getManager());
              }
            }
            return result;
          }
        }
        return null;
      }

    });
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
            } else {
              super.fun(m, instruction);
            }
          }
        };
        final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
        final DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
        final List<TIntObjectHashMap<TIntHashSet>> dfaResult = engine.performDFA();
        return Result.create(Pair.create(dfaInstance, dfaResult), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static PsiType getDefinitionType(Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        return getInitializerType(element);
      }
    }
    if (instruction instanceof AssertionInstruction) {
      final AssertionInstruction assertionInstruction = (AssertionInstruction)instruction;
      final PsiElement element = assertionInstruction.getElement();
      if (element instanceof GrInstanceOfExpression && !assertionInstruction.isNegate()) {
        final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
        if (typeElement != null) {
          return typeElement.getType();
        }
      }
    }
    return null;
  }


  @Nullable
  public static ReadWriteVariableInstruction findInstruction(final GrReferenceExpression refExpr, final Instruction[] flow) {
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
