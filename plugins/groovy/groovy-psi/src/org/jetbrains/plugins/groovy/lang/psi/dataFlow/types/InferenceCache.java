// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalExpressionFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.LazyKt.lazyPub;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.findReadDependencies;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.getVarIndexes;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper.getDefUseMaps;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper.isSharedVariable;
import static org.jetbrains.plugins.groovy.util.GraphKt.findNodesOutsideCycles;
import static org.jetbrains.plugins.groovy.util.GraphKt.mapGraph;

class InferenceCache {

  private final GrControlFlowOwner myScope;
  private final Instruction[] myFlow;
  private final Map<PsiElement, List<Instruction>> myFromByElements;

  private final Lazy<TObjectIntHashMap<VariableDescriptor>> myVarIndexes;
  private final Lazy<List<DefinitionMap>> myDefinitionMaps;

  private final AtomicReference<List<TypeDfaState>> myVarTypes;
  private final SharedVariableInferenceCache mySharedVariableInferenceCache;
  private final Set<Instruction> myTooComplexInstructions = ContainerUtil.newConcurrentSet();

  InferenceCache(@NotNull GrControlFlowOwner scope) {
    myScope = scope;
    myFlow = scope.getControlFlow();
    myVarIndexes = lazyPub(() -> getVarIndexes(myScope));
    myDefinitionMaps = lazyPub(() -> getDefUseMaps(myFlow, myVarIndexes.getValue()));
    mySharedVariableInferenceCache = new SharedVariableInferenceCache(scope);
    myFromByElements = Arrays.stream(myFlow).filter(it -> it.getElement() != null).collect(Collectors.groupingBy(Instruction::getElement));
    List<TypeDfaState> noTypes = new ArrayList<>();
    for (int i = 0; i < myFlow.length; i++) {
      noTypes.add(new TypeDfaState());
    }
    myVarTypes = new AtomicReference<>(noTypes);
  }

  boolean isTooComplexToAnalyze() {
    return myDefinitionMaps.getValue() == null;
  }

  @Nullable
  PsiType getInferredType(@NotNull VariableDescriptor descriptor,
                          @NotNull Instruction instruction,
                          boolean mixinOnly) {
    return getInferredType(descriptor, instruction, mixinOnly, emptyMap());
  }

  @Nullable
  PsiType getInferredType(@NotNull VariableDescriptor descriptor,
                          @NotNull Instruction instruction,
                          boolean mixinOnly,
                          @NotNull Map<VariableDescriptor, DFAType> initialState) {
    if (myTooComplexInstructions.contains(instruction)) return null;

    final List<DefinitionMap> definitionMaps = myDefinitionMaps.getValue();
    if (definitionMaps == null) {
      return null;
    }

    TypeDfaState cache = myVarTypes.get().get(instruction.num());
    if (!cache.containsVariable(descriptor)) {
      Predicate<Instruction> mixinPredicate = mixinOnly ? (e) -> e instanceof MixinTypeInstruction : (e) -> true;
      DFAFlowInfo flowInfo = collectFlowInfo(definitionMaps, instruction, descriptor, initialState, mixinPredicate);
      List<TypeDfaState> dfaResult = performTypeDfa(myScope, myFlow, flowInfo);
      if (dfaResult == null) {
        myTooComplexInstructions.addAll(flowInfo.getInterestingInstructions());
      }
      else {
        Set<Instruction> stored = flowInfo.getInterestingInstructions();
        stored.add(instruction);
        cacheDfaResult(dfaResult, stored);
      }
    }
    DFAType dfaType = getCachedInferredType(descriptor, instruction);
    return dfaType == null ? null : dfaType.getResultType(myScope.getManager());
  }

  @Nullable
  private List<TypeDfaState> performTypeDfa(@NotNull GrControlFlowOwner owner,
                                            Instruction @NotNull [] flow,
                                            @NotNull DFAFlowInfo flowInfo) {
    final TypeDfaInstance dfaInstance =
      new TypeDfaInstance(flow, flowInfo, this, owner.getManager(), new InitialTypeProvider(owner, flowInfo.getInitialTypes()));
    final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
    return new DFAEngine<>(flow, dfaInstance, semilattice).performDFAWithTimeout();
  }

  @Nullable
  DFAType getCachedInferredType(@NotNull VariableDescriptor descriptor, @NotNull Instruction instruction) {
    return myVarTypes.get().get(instruction.num()).getVariableType(descriptor);
  }

  private DFAFlowInfo collectFlowInfo(@NotNull List<DefinitionMap> definitionMaps,
                                      @NotNull Instruction instruction,
                                      @NotNull VariableDescriptor descriptor,
                                      @NotNull Map<VariableDescriptor, DFAType> initialState,
                                      @NotNull Predicate<? super Instruction> predicate) {
    Map<Pair<Instruction, VariableDescriptor>, Collection<Pair<Instruction, VariableDescriptor>>> interesting = new LinkedHashMap<>();
    LinkedList<Pair<Instruction, VariableDescriptor>> queue = new LinkedList<>();
    queue.add(Pair.create(instruction, descriptor));
    Set<Instruction> dependentOnSharedVariables = new LinkedHashSet<>();
    List<Pair<Instruction, Set<? extends VariableDescriptor>>> closureInstructions = getClosureInstructionsWithForeigns();

    while (!queue.isEmpty()) {
      Pair<Instruction, VariableDescriptor> pair = queue.removeFirst();
      if (!interesting.containsKey(pair)) {
        Set<Pair<Instruction, VariableDescriptor>> dependencies =
          findDependencies(definitionMaps, closureInstructions, pair.first, pair.second);
        interesting.put(pair, dependencies);
        if (dependencies.stream().anyMatch(it -> isSharedVariable(it.second))) {
          dependentOnSharedVariables.add(pair.first);
        }
        dependencies.forEach(queue::addLast);
      }
    }
    Set<Instruction> interestingInstructions = interesting.keySet().stream()
      .map(it -> it.getFirst())
      .filter(predicate).collect(Collectors.toSet());
    Set<Instruction> acyclicInstructions = findNodesOutsideCycles(mapGraph(interesting)).stream()
      .map(it -> it.getFirst())
      .filter(predicate)
      .collect(Collectors.toSet());
    Set<VariableDescriptor> interestingDescriptors = interesting.keySet().stream()
      .map(it -> it.getSecond())
      .collect(Collectors.toSet());
    return new DFAFlowInfo(initialState,
                           interestingInstructions,
                           acyclicInstructions,
                           interestingDescriptors,
                           dependentOnSharedVariables);
  }

  private List<Pair<Instruction, Set<? extends VariableDescriptor>>> getClosureInstructionsWithForeigns() {
    if (PsiUtil.isCompileStatic(myScope)) {
      return emptyList();
    }
    List<Pair<Instruction, Set<? extends VariableDescriptor>>> closureInstructions = new ArrayList<>();
    for (Instruction closureInstruction : myFlow) {
      PsiElement closure = closureInstruction.getElement();
      if (closure instanceof GrFunctionalExpression) {
        GrControlFlowOwner owner =
          Objects.requireNonNull(FunctionalExpressionFlowUtil.getControlFlowOwner((GrFunctionalExpression)closure));
        Set<ResolvedVariableDescriptor> foreignVariables =
          ControlFlowUtils.getForeignVariableDescriptors(owner, ReadWriteVariableInstruction::isWrite);
        closureInstructions.add(Pair.create(closureInstruction, foreignVariables));
      }
    }
    return closureInstructions;
  }

  @NotNull
  private Set<Pair<Instruction, VariableDescriptor>> findDependencies(@NotNull List<DefinitionMap> definitionMaps,
                                                                      @NotNull List<Pair<Instruction, Set<? extends VariableDescriptor>>> closureInstructions,
                                                                      @NotNull Instruction instruction,
                                                                      @NotNull VariableDescriptor descriptor) {
    DefinitionMap definitionMap = definitionMaps.get(instruction.num());
    int varIndex = myVarIndexes.getValue().get(descriptor);
    int[] definitions = definitionMap.getDefinitions(varIndex);

    LinkedHashSet<Pair<Instruction, VariableDescriptor>> pairs = new LinkedHashSet<>();

    int latestDefinition = Math.max(instruction.num(), definitions == null ? 0 : ArrayUtil.max(definitions));
    for (Pair<Instruction, Set<? extends VariableDescriptor>> closureInstruction : closureInstructions) {
      if (closureInstruction.first.num() > latestDefinition) break;
      if (closureInstruction.second.contains(descriptor)) {
        pairs.add(Pair.create(closureInstruction.first, descriptor));
      }
    }

    if (definitions == null) return pairs;

    for (int defIndex : definitions) {
      Instruction write = myFlow[defIndex];
      if (write != instruction) {
        pairs.add(Pair.create(write, descriptor));
      }
      for (ReadWriteVariableInstruction dependency : findReadDependencies(write, it -> myFromByElements.getOrDefault(it, emptyList()))) {
        pairs.add(Pair.create(dependency, dependency.getDescriptor()));
      }
    }
    return pairs;
  }

  private void cacheDfaResult(@NotNull List<TypeDfaState> dfaResult,
                              Set<Instruction> storingInstructions) {
    myVarTypes.accumulateAndGet(dfaResult, (oldState, newState) -> addDfaResult(oldState, newState, storingInstructions));
  }

  @NotNull SharedVariableInferenceCache getSharedVariableInferenceCache() {
    return mySharedVariableInferenceCache;
  }

  @NotNull
  private static List<TypeDfaState> addDfaResult(@NotNull List<TypeDfaState> oldTypes,
                                                 @NotNull List<TypeDfaState> dfaResult,
                                                 @NotNull Set<Instruction> storingInstructions) {
    List<TypeDfaState> newTypes = new ArrayList<>(oldTypes);
    Set<Integer> interestingInstructionNums = storingInstructions.stream().map(Instruction::num).collect(Collectors.toSet());
    for (int i = 0; i < dfaResult.size(); i++) {
      if (interestingInstructionNums.contains(i)) {
        newTypes.set(i, newTypes.get(i).mergeWith(dfaResult.get(i)));
      }
    }
    return newTypes;
  }
}
