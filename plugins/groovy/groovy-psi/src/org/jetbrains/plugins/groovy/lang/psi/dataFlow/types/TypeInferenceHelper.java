// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.InferenceContext;
import org.jetbrains.plugins.groovy.lang.psi.impl.PartialContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory.createDescriptor;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.checkNestedContext;
import static org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil.isCompileStatic;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses;
import static org.jetbrains.plugins.groovy.lang.typing.TuplesKt.getMultiAssignmentType;

/**
 * @author ven
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class TypeInferenceHelper {
  private static final Logger LOG = Logger.getInstance(TypeInferenceHelper.class);

  private static final ThreadLocal<InferenceContext> ourInferenceContext = new ThreadLocal<>();

  static <T> T doInference(@NotNull Map<VariableDescriptor, DFAType> bindings, @NotNull Computable<? extends T> computation) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      checkNestedContext();
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

    final GroovyReference rValueReference = refExpr.getRValueReference();
    PsiElement resolve = rValueReference == null ? null : rValueReference.resolve();
    boolean mixinOnly = resolve instanceof GrField && isCompileStatic(refExpr);

    final VariableDescriptor descriptor = createDescriptor(refExpr);
    if (descriptor == null) return null;

    final ReadWriteVariableInstruction rwInstruction = ControlFlowUtils.findRWInstruction(refExpr, scope.getControlFlow());
    if (rwInstruction == null) return null;

    final InferenceCache cache = getInferenceCache(scope);
    final PsiType sharedType = getSharedVariableType(descriptor);
    return sharedType != null ? sharedType : cache.getInferredType(descriptor, rwInstruction, mixinOnly);
  }

  @Nullable
  public static PsiType getInferredType(VariableDescriptor descriptor, Instruction instruction, GrControlFlowOwner scope) {
    InferenceCache cache = getInferenceCache(scope);
    return cache.getInferredType(descriptor, instruction, false);
  }

  @Nullable
  public static PsiType getVariableTypeInContext(@Nullable PsiElement context, @NotNull GrVariable variable) {
    if (context == null) return variable.getType();
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(context);
    if (scope == null) return null;

    final Instruction nearest = ControlFlowUtils.findNearestInstruction(context, scope.getControlFlow());
    if (nearest == null) return null;
    boolean mixinOnly = variable instanceof GrField && isCompileStatic(scope);

    final InferenceCache cache = getInferenceCache(scope);
    final VariableDescriptor descriptor = createDescriptor(variable);
    final PsiType sharedType = getSharedVariableType(descriptor);
    if (sharedType != null) {
      return sharedType;
    }
    final PsiType inferredType = cache.getInferredType(descriptor, nearest, mixinOnly);
    return inferredType != null ? inferredType : variable.getType();
  }

  public static boolean isTooComplexTooAnalyze(@NotNull GrControlFlowOwner scope) {
    return getInferenceCache(scope).isTooComplexToAnalyze();
  }

  @NotNull
  static InferenceCache getInferenceCache(@NotNull final GrControlFlowOwner scope) {
    return CachedValuesManager.getCachedValue(scope, () -> Result.create(new InferenceCache(scope), MODIFICATION_COUNT));
  }

  static boolean isSharedVariable(@NotNull VariableDescriptor descriptor) {
    SharedVariableInferenceCache cache = getSharedVariableCache(descriptor);
    return cache != null && cache.getSharedVariableDescriptors().contains(descriptor);
  }

  private static @Nullable PsiType getSharedVariableType(@NotNull VariableDescriptor descriptor) {
    SharedVariableInferenceCache cache = getSharedVariableCache(descriptor);
    return cache == null ? null : cache.getSharedVariableType(descriptor);
  }

  private static @Nullable SharedVariableInferenceCache getSharedVariableCache(@NotNull VariableDescriptor descriptor) {
    if (descriptor instanceof ResolvedVariableDescriptor) {
      GrControlFlowOwner trueOwner = ControlFlowUtils.findControlFlowOwner(((ResolvedVariableDescriptor)descriptor).getVariable());
      if (trueOwner == null) {
        return null;
      }
      return getInferenceCache(trueOwner).getSharedVariableInferenceCache();
    }
    else {
      // this is definitely not a local variable
      return null;
    }
  }

  @Nullable
  static List<DefinitionMap> getDefUseMaps(Instruction @NotNull [] flow, @NotNull Object2IntMap<VariableDescriptor> varIndexes) {
    final ReachingDefinitionsDfaInstance dfaInstance = new TypesReachingDefinitionsInstance(flow, varIndexes);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<DefinitionMap> engine = new DFAEngine<>(flow, dfaInstance, lattice);
    List<DefinitionMap> defUseMaps = engine.performDFAWithTimeout();
    if (defUseMaps != null) {
      internalize(defUseMaps);
    }
    return defUseMaps;
  }

  /**
   * Optimizes maps for caching by limiting number of different referenced instances.
   */
  private static void internalize(@NotNull List<DefinitionMap> maps) {
    Map<DefinitionMap, DefinitionMap> internedMaps = new HashMap<>();
    maps.replaceAll(map -> internedMaps.computeIfAbsent(map, Function.identity()));
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
        if (rValue != null) {
          int idx = list.indexOf(element);
          if (idx >= 0) {
            return getMultiAssignmentType(rValue, idx);
          }
        }
      }
    }
    if (parent instanceof GrUnaryExpression) {
      GrUnaryExpression unary = (GrUnaryExpression)parent;
      if (TokenSets.POSTFIX_UNARY_OP_SET.contains(unary.getOperationTokenType())) {
        return unary.getOperationType();
      }
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

  static boolean isSimpleEnoughForAugmenting(Instruction @NotNull [] flow) {
    // in large flows there is a lot of variables, so minor inability to infer type for a parameter should not be noticeable.
    // on the other side, people may omit types of parameters in short methods, so augmenting may be useful there
    return flow.length < 50;
  }
}
