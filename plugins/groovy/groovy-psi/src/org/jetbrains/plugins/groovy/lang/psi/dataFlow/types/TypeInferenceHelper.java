// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.InferenceContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.*;

/**
 * @author ven
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class TypeInferenceHelper {
  private static final ThreadLocal<InferenceContext> ourInferenceContext = new ThreadLocal<>();

  private static <T> T doInference(@NotNull Map<String, PsiType> bindings, @NotNull Computable<T> computation) {
    InferenceContext old = ourInferenceContext.get();
    ourInferenceContext.set(new InferenceContext.PartialContext(bindings));
    try {
      return computation.compute();
    }
    finally {
      ourInferenceContext.set(old);
    }
  }

  public static InferenceContext getCurrentContext() {
    InferenceContext context = ourInferenceContext.get();
    return context != null ? context : InferenceContext.TOP_CONTEXT;
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
  private static Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> getDefUseMaps(@NotNull final GrControlFlowOwner scope) {
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
            } else {
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
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null) {
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
        return rvalue != null ? rvalue.getType() : null; //don't try to infer assignment type in case of index property because of infinite recursion (example: a[2]+=4)
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
            PsiType[] componentTypes = ((GrTupleType)rType).getComponentTypes();
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

  @Nullable
  public static GrExpression getInitializerFor(GrExpression lValue) {
    final PsiElement parent = lValue.getParent();
    if (parent instanceof GrAssignmentExpression) return ((GrAssignmentExpression)parent).getRValue();
    if (parent instanceof GrTuple) {
      final int i = ((GrTuple)parent).indexOf(lValue);
      final GrTupleAssignmentExpression pparent = ((GrTuple)parent).getParent();
      LOG.assertTrue(pparent != null);

      final GrExpression rValue = pparent.getRValue();
      if (rValue instanceof GrListOrMap && !((GrListOrMap)rValue).isMap()) {
        final GrExpression[] initializers = ((GrListOrMap)rValue).getInitializers();
        if (initializers.length < i) return initializers[i];
      }
    }

    return null;
  }

  static class TypeDfaInstance implements DfaInstance<TypeDfaState> {
    private final GrControlFlowOwner myScope;
    private final Instruction[] myFlow;
    private final Set<Instruction> myInteresting;
    private final InferenceCache myCache;

    TypeDfaInstance(@NotNull GrControlFlowOwner scope, @NotNull Instruction[] flow, @NotNull Set<Instruction> interesting, @NotNull InferenceCache cache) {
      myScope = scope;
      myFlow = flow;
      myInteresting = interesting;
      myCache = cache;
    }

    @Override
    public void fun(@NotNull final TypeDfaState state, @NotNull final Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        handleVariableWrite(state, (ReadWriteVariableInstruction)instruction);
      }
      else if (instruction instanceof MixinTypeInstruction) {
        handleMixin(state, (MixinTypeInstruction)instruction);
      }
    }

    private void handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
      final String varName = instruction.getVariableName();
      if (varName == null) return;

      updateVariableType(state, instruction, varName, (NullableComputable<DFAType>)() -> {
        ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
        assert originalInstr != null && !originalInstr.isWrite();

        DFAType original = state.getVariableType(varName);
        if (original == null) {
          original = DFAType.create(null);
        }
        original = original.negate(originalInstr);
        original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
        return original;
      });
    }

    private void handleVariableWrite(TypeDfaState state, ReadWriteVariableInstruction instruction) {
      final PsiElement element = instruction.getElement();
      if (element != null && instruction.isWrite()) {
        updateVariableType(state, instruction, instruction.getVariableName(),
                           () -> DFAType.create(getInitializerType(element)));
      }
    }

    private void updateVariableType(@NotNull TypeDfaState state, @NotNull Instruction instruction, @NotNull String variableName, @NotNull Computable<DFAType> computation) {
      if (!myInteresting.contains(instruction)) {
        state.removeBinding(variableName);
        return;
      }

      DFAType type = myCache.getCachedInferredType(variableName, instruction);
      if (type == null) {
        type = doInference(state.getBindings(instruction), computation);
      }
      state.putType(variableName, type);
    }

    @Override
    @NotNull
    public TypeDfaState initial() {
      return new TypeDfaState();
    }
  }

  private static class InferenceCache {
    final GrControlFlowOwner scope;
    final Instruction[] flow;
    final AtomicReference<List<TypeDfaState>> varTypes;
    final Set<Instruction> tooComplex = ContainerUtil.newConcurrentSet();

    InferenceCache(final GrControlFlowOwner scope) {
      this.scope = scope;
      this.flow = scope.getControlFlow();
      List<TypeDfaState> noTypes = new ArrayList<>();
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < flow.length; i++) {
        noTypes.add(new TypeDfaState());
      }
      varTypes = new AtomicReference<>(noTypes);
    }

    @Nullable
    private PsiType getInferredType(@NotNull String variableName, @NotNull Instruction instruction, boolean mixinOnly) {
      if (tooComplex.contains(instruction)) return null;

      TypeDfaState cache = varTypes.get().get(instruction.num());
      if (!cache.containsVariable(variableName)) {
        Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> defUse = getDefUseMaps(scope);
        if (defUse == null) {
          tooComplex.add(instruction);
          return null;
        }

        Predicate<Instruction> mixinPredicate = mixinOnly ? (e) -> e instanceof MixinTypeInstruction : (e) -> true;
        Set<Instruction> interesting = collectRequiredInstructions(instruction, variableName, defUse, mixinPredicate);
        List<TypeDfaState> dfaResult = performTypeDfa(scope, flow, interesting);
        if (dfaResult == null) {
          tooComplex.addAll(interesting);
        } else {
          cacheDfaResult(dfaResult);
        }
      }
      DFAType dfaType = getCachedInferredType(variableName, instruction);
      return dfaType == null ? null : dfaType.getResultType();
    }

    @Nullable
    private List<TypeDfaState> performTypeDfa(@NotNull GrControlFlowOwner owner, @NotNull Instruction[] flow, @NotNull Set<Instruction> interesting) {
      final TypeDfaInstance dfaInstance = new TypeDfaInstance(owner, flow, interesting, this);
      final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
      return new DFAEngine<>(flow, dfaInstance, semilattice).performDFAWithTimeout();
    }

    @Nullable
    DFAType getCachedInferredType(@NotNull String variableName, @NotNull Instruction instruction) {
      DFAType dfaType = varTypes.get().get(instruction.num()).getVariableType(variableName);
      return dfaType == null ? null : dfaType.negate(instruction);
    }

    private Set<Instruction> collectRequiredInstructions(@NotNull Instruction instruction,
                                                         @NotNull String variableName,
                                                         @NotNull Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> defUse,
                                                         @NotNull Predicate<Instruction> predicate
                                                         ) {
      Set<Instruction> interesting = ContainerUtil.newHashSet(instruction);
      LinkedList<Pair<Instruction,String>> queue = ContainerUtil.newLinkedList();
      queue.add(Pair.create(instruction, variableName));
      while (!queue.isEmpty()) {
        Pair<Instruction, String> pair = queue.removeFirst();
        for (Pair<Instruction, String> dep : findDependencies(defUse, pair.first, pair.second)) {
          if (interesting.add(dep.first)) {
            queue.addLast(dep);
          }
        }
      }

      return interesting.stream().filter(predicate).collect(Collectors.toSet());
    }

    @NotNull
    private Set<Pair<Instruction,String>> findDependencies(@NotNull Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> defUse,
                                                           @NotNull Instruction insn,
                                                           @NotNull String varName) {
      DefinitionMap definitionMap = defUse.second.get(insn.num());
      int varIndex = defUse.first.getVarIndex(varName);
      int[] definitions = definitionMap.getDefinitions(varIndex);
      if (definitions == null) return Collections.emptySet();

      LinkedHashSet<Pair<Instruction, String>> pairs = ContainerUtil.newLinkedHashSet();
      for (int defIndex : definitions) {
        Instruction write = flow[defIndex];
        pairs.add(Pair.create(write, varName));
        PsiElement statement = findDependencyScope(write.getElement());
        if (statement != null) {
          pairs.addAll(findAllInstructionsInside(statement));
        }
      }
      return pairs;
    }

    @NotNull
    private List<Pair<Instruction, String>> findAllInstructionsInside(@NotNull PsiElement scope) {
      final List<Pair<Instruction, String>> result = ContainerUtil.newArrayList();
      scope.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof GrReferenceExpression && !((GrReferenceExpression)element).isQualified()) {
            String varName = ((GrReferenceExpression)element).getReferenceName();
            if (varName != null) {
              for (Instruction dependency : ControlFlowUtils.findAllInstructions(element, flow)) {
                result.add(Pair.create(dependency, varName));
              }
            }
          }
          super.visitElement(element);
        }
      });
      return result;
    }

    @Nullable
    private static PsiElement findDependencyScope(@Nullable PsiElement element) {
      return PsiTreeUtil.findFirstParent(element,
                                         element1 -> isExpressionStatement(element1) ||
                                                     !(element1.getParent() instanceof GrExpression));
    }

    private void cacheDfaResult(@NotNull List<TypeDfaState> dfaResult) {
      while (true) {
        List<TypeDfaState> oldTypes = varTypes.get();
        if (varTypes.compareAndSet(oldTypes, addDfaResult(dfaResult, oldTypes))) {
          return;
        }
      }
    }

    @NotNull
    private static List<TypeDfaState> addDfaResult(@NotNull List<TypeDfaState> dfaResult, @NotNull List<TypeDfaState> oldTypes) {
      List<TypeDfaState> newTypes = new ArrayList<>(oldTypes);
      for (int i = 0; i < dfaResult.size(); i++) {
        newTypes.set(i, newTypes.get(i).mergeWith(dfaResult.get(i)));
      }
      return newTypes;
    }
  }

}

