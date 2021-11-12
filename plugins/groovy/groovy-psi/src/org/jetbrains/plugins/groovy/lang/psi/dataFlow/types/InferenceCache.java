// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalExpressionFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.LazyKt.lazyPub;
import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.findReadDependencies;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.getVarIndexes;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper.getDefUseMaps;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper.isSharedVariable;
import static org.jetbrains.plugins.groovy.util.GraphKt.findNodesOutsideCycles;
import static org.jetbrains.plugins.groovy.util.GraphKt.mapGraph;

final class InferenceCache {
  private final @NotNull GrControlFlowOwner myScope;
  private final Instruction[] myFlow;
  private final Map<PsiElement, List<Instruction>> myFromByElements;

  private final Lazy<Object2IntMap<VariableDescriptor>> myVarIndexes;
  private final Lazy<List<DefinitionMap>> myDefinitionMaps;

  private final AtomicReference<Map<VariableDescriptor, DFAType>>[] myVarTypes;
  //private final SharedVariableInferenceCache mySharedVariableInferenceCache;
  private final Set<Instruction> myTooComplexInstructions = ContainerUtil.newConcurrentSet();
  /**
   * Instructions outside any cycle. The DFA has straightforward direction of these instructions, so it is safe
   * to apply additional optimizations on this flow
   */
  private final Set<Instruction> simpleInstructions;

  InferenceCache(@NotNull GrControlFlowOwner scope) {
    myScope = scope;
    myFlow = scope.getControlFlow();
    myVarIndexes = lazyPub(() -> getVarIndexes(myScope));
    myDefinitionMaps = lazyPub(() -> getDefUseMaps(myFlow, myVarIndexes.getValue()));
    mySharedVariableInferenceCache = new SharedVariableInferenceCache(scope);
    myFromByElements = Arrays.stream(myFlow).filter(it -> it.getElement() != null).collect(Collectors.groupingBy(Instruction::getElement));
    //noinspection unchecked
    AtomicReference<Map<VariableDescriptor, DFAType>>[] basicTypes = new AtomicReference[myFlow.length];
    for (int i = 0; i < myFlow.length; i++) {
      basicTypes[i] = new AtomicReference<>(new HashMap<>());
    }
    myVarTypes = basicTypes;
    simpleInstructions = findNodesOutsideCycles(mapGraph(Arrays.stream(myFlow).collect(Collectors.toMap(instr -> instr, instr -> {
      List<Instruction> list = new ArrayList<>();
      instr.allSuccessors().forEach(list::add);
      return list;
    }))));
  }

  boolean isTooComplexToAnalyze() {
    return myDefinitionMaps.getValue() == null;
  }

  @Nullable
  PsiType getInferredType(@NotNull VariableDescriptor descriptor,
                          @NotNull Instruction instruction,
                          boolean mixinOnly) {
    if (myTooComplexInstructions.contains(instruction)) return null;

    final List<DefinitionMap> definitionMaps = myDefinitionMaps.getValue();
    if (definitionMaps == null || !isDescriptorAvailable(descriptor)) {
      return null;
    }

    Map<VariableDescriptor, DFAType> cache = myVarTypes[instruction.num()].get();
    if (!cache.containsKey(descriptor)) {
      Predicate<Instruction> mixinPredicate = mixinOnly ? (e) -> e instanceof MixinTypeInstruction : (e) -> true;
      DFAFlowInfo flowInfo = collectFlowInfo(definitionMaps, instruction, descriptor, mixinPredicate);
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
    final TypeDfaInstance dfaInstance = new TypeDfaInstance(flow, flowInfo, this, owner.getManager(), new InitialTypeProvider(owner));
    final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager(), myVarIndexes.getValue());
    return new DFAEngine<>(flow, dfaInstance, semilattice).performDFAWithTimeout();
  }

  @Nullable
  DFAType getCachedInferredType(@NotNull VariableDescriptor descriptor, @NotNull Instruction instruction) {
    return myVarTypes[instruction.num()].get().get(descriptor);
  }

  void publishDescriptor(@NotNull TypeDfaState intermediateState, @NotNull Instruction instruction) {
    if (simpleInstructions.contains(instruction) && TypeInferenceHelper.getCurrentContext() == TypeInferenceHelper.getTopContext()) {
      myVarTypes[instruction.num()].getAndUpdate(
        oldState -> TypesSemilattice.mergeForCaching(oldState, intermediateState, myVarIndexes.getValue()));
    }
  }

  private DFAFlowInfo collectFlowInfo(@NotNull List<DefinitionMap> definitionMaps,
                                      @NotNull Instruction instruction,
                                      @NotNull VariableDescriptor descriptor,
                                      @NotNull Predicate<? super Instruction> predicate) {
    Map<Pair<Instruction, VariableDescriptor>, Collection<Pair<Instruction, VariableDescriptor>>> interesting = new LinkedHashMap<>();
    LinkedList<Pair<Instruction, VariableDescriptor>> queue = new LinkedList<>();
    queue.add(Pair.create(instruction, descriptor));
    Set<Instruction> dependentOnSharedVariables = new LinkedHashSet<>();
    List<Pair<Instruction, Set<? extends VariableDescriptor>>> closureInstructions;
    if (FunctionalExpressionFlowUtil.isNestedFlowProcessingAllowed()) {
      closureInstructions = getClosureInstructionsWithForeigns();
    } else {
      closureInstructions = emptyList();
    }

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
    return new DFAFlowInfo(interestingInstructions,
                           acyclicInstructions,
                           interestingDescriptors,
                           dependentOnSharedVariables,
                           myVarIndexes.getValue());
  }

  @NotNull
  private Set<Pair<Instruction, VariableDescriptor>> findDependencies(@NotNull List<DefinitionMap> definitionMaps,
                                                                      @NotNull List<Pair<Instruction, Set<? extends VariableDescriptor>>> closureInstructions,
                                                                      @NotNull Instruction instruction,
                                                                      @NotNull VariableDescriptor descriptor) {
    DefinitionMap definitionMap = definitionMaps.get(instruction.num());
    int varIndex = myVarIndexes.getValue().getInt(descriptor);
    int[] definitions = definitionMap.getDefinitions(varIndex);

    LinkedHashSet<Pair<Instruction, VariableDescriptor>> pairs = new LinkedHashSet<>();

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
    for (var instruction : storingInstructions) {
      int index = instruction.num();
      myVarTypes[index].getAndUpdate(oldState -> TypesSemilattice.mergeForCaching(oldState, dfaResult.get(index), myVarIndexes.getValue()));
    }
  }

  //@NotNull SharedVariableInferenceCache getSharedVariableInferenceCache() {
  //  return mySharedVariableInferenceCache;
  //}

  private boolean isDescriptorAvailable(@NotNull VariableDescriptor descriptor) {
    return myVarIndexes.getValue().containsKey(descriptor);
  }
}
