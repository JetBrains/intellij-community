// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.InferenceContext;
import org.jetbrains.plugins.groovy.lang.psi.impl.PartialContext;

import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.*;

/**
 * @author ven
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class TypeInferenceHelper {

  private static boolean allowNestedContext = true;

  private static void setAllowNestedContext(boolean value, @NotNull Disposable parent) {
    boolean oldValue = allowNestedContext;
    if (oldValue == value) return;
    allowNestedContext = value;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        allowNestedContext = oldValue;
      }
    });
  }

  @TestOnly
  public static void disallowNestedContext(@NotNull Disposable parent) {
    setAllowNestedContext(false, parent);
  }

  @TestOnly
  public static void forceAllowNestedContext(@NotNull Disposable parent) {
    setAllowNestedContext(true, parent);
  }

  private static final ThreadLocal<InferenceContext> ourInferenceContext = new ThreadLocal<>();

  static <T> T doInference(@NotNull Map<String, PsiType> bindings, @NotNull Computable<? extends T> computation) {
    if (!allowNestedContext && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("Unexpected attempt to infer in nested context");
    }
    return withContext(new PartialContext(bindings), computation);
  }

  private static <T> T withContext(@NotNull InferenceContext context, @NotNull Computable<? extends T> computation) {
    InferenceContext previous = ourInferenceContext.get();
    ourInferenceContext.set(context);
    try {
      return computation.compute();
    }
    finally {
      ourInferenceContext.set(previous);
    }
  }

  @NotNull
  @Contract(pure = true)
  public static InferenceContext getCurrentContext() {
    InferenceContext context = ourInferenceContext.get();
    return context != null ? context : getTopContext();
  }

  public static <T> T inTopContext(@NotNull Computable<? extends T> computation) {
    return withContext(getTopContext(), computation);
  }

  @NotNull
  @Contract(pure = true)
  public static InferenceContext getTopContext() {
    return InferenceContext.TOP_CONTEXT;
  }

  @Nullable
  public static PsiType getInferredType(@NotNull final GrReferenceExpression refExpr) {
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(refExpr);
    if (scope == null) return null;
    PsiElement resolve = refExpr.resolve();
    boolean mixinOnly = resolve instanceof GrField && isCompileStatic(refExpr);

    final String referenceName = refExpr.getReferenceName();
    if (referenceName == null) return null;

    final ReadWriteVariableInstruction rwInstruction = ControlFlowUtils.findRWInstruction(refExpr, scope.getControlFlow());
    if (rwInstruction == null) return null;

    return getInferenceCache(scope).getInferredType(referenceName, rwInstruction, mixinOnly);
  }

  @Nullable
  public static PsiType getInferredType(String referenceName, Instruction instruction, GrControlFlowOwner scope) {
    return getInferenceCache(scope).getInferredType(referenceName, instruction, false);
  }

  @Nullable
  public static PsiType getVariableTypeInContext(@Nullable PsiElement context, @NotNull GrVariable variable) {
    if (context == null) return variable.getType();
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(context);
    if (scope == null) return null;

    final Instruction nearest = ControlFlowUtils.findNearestInstruction(context, scope.getControlFlow());
    if (nearest == null) return null;
    boolean mixinOnly = variable instanceof GrField && isCompileStatic(scope);
    PsiType inferredType = getInferenceCache(scope).getInferredType(variable.getName(), nearest, mixinOnly);
    return inferredType != null ? inferredType : variable.getType();
  }


  @NotNull
  private static InferenceCache getInferenceCache(@NotNull final GrControlFlowOwner scope) {
    return CachedValuesManager.getCachedValue(scope, () -> CachedValueProvider.Result
      .create(new InferenceCache(scope), PsiModificationTracker.MODIFICATION_COUNT));
  }

  public static boolean isTooComplexTooAnalyze(@NotNull GrControlFlowOwner scope) {
    return getDefUseMaps(scope) == null;
  }

  @Nullable
  static Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> getDefUseMaps(@NotNull final GrControlFlowOwner scope) {
    return CachedValuesManager.getCachedValue(scope, new CachedValueProvider<Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>>>() {
      @Override
      public Result<Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>>> compute() {
        final Instruction[] flow = scope.getControlFlow();
        final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow) {
          @Override
          public void fun(@NotNull DefinitionMap m, @NotNull Instruction instruction) {
            if (instruction instanceof MixinTypeInstruction) {
              int varIndex = getVarIndex(((MixinTypeInstruction)instruction).getVariableName());
              if (varIndex > 0) {
                m.registerDef(instruction, varIndex);
              }
            }
            else {
              super.fun(m, instruction);
            }
          }
        };
        final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
        final DFAEngine<DefinitionMap> engine = new DFAEngine<>(flow, dfaInstance, lattice);
        final List<DefinitionMap> dfaResult = engine.performDFAWithTimeout();
        Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> result = dfaResult == null ? null : Pair.create(dfaInstance, dfaResult);
        return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  public static PsiType getInitializerType(final PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).getQualifierExpression() == null) {
      return getInitializerTypeFor(element);
    }

    if (element instanceof GrVariable) {
      return ((GrVariable)element).getTypeGroovy();
    }

    return null;
  }

  @Nullable
  public static PsiType getInitializerTypeFor(PsiElement element) {
    final PsiElement parent = skipParentheses(element.getParent(), true);
    if (parent instanceof GrAssignmentExpression) {
      if (element instanceof GrIndexProperty) {
        final GrExpression rvalue = ((GrAssignmentExpression)parent).getRValue();
        return rvalue != null
               ? rvalue.getType()
               : null; //don't try to infer assignment type in case of index property because of infinite recursion (example: a[2]+=4)
      }
      return ((GrAssignmentExpression)parent).getType();
    }

    if (parent instanceof GrTuple) {
      GrTuple list = (GrTuple)parent;
      GrTupleAssignmentExpression assignment = list.getParent();
      if (assignment != null) {
        final GrExpression rValue = assignment.getRValue();
        int idx = list.indexOf(element);
        if (idx >= 0 && rValue != null) {
          PsiType rType = rValue.getType();
          if (rType instanceof GrTupleType) {
            List<PsiType> componentTypes = ((GrTupleType)rType).getComponentTypes();
            if (idx < componentTypes.size()) return componentTypes.get(idx);
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

  @Nullable
  public static GrExpression getInitializerFor(GrExpression lValue) {
    final PsiElement parent = lValue.getParent();
    if (parent instanceof GrAssignmentExpression) return ((GrAssignmentExpression)parent).getRValue();
    if (parent instanceof GrTuple) {
      final int i = ((GrTuple)parent).indexOf(lValue);
      final GrTupleAssignmentExpression grandParent = ((GrTuple)parent).getParent();
      LOG.assertTrue(grandParent != null);

      final GrExpression rValue = grandParent.getRValue();
      if (rValue instanceof GrListOrMap && !((GrListOrMap)rValue).isMap()) {
        final GrExpression[] initializers = ((GrListOrMap)rValue).getInitializers();
        if (i < initializers.length) return initializers[i];
      }
    }

    return null;
  }
}
