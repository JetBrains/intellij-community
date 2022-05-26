// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.LazyKt.lazyPub;
import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.findReadDependencies;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.getSimpleInstructions;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper.getDefUseMaps;
import static org.jetbrains.plugins.groovy.util.GraphKt.findNodesOutsideCycles;
import static org.jetbrains.plugins.groovy.util.GraphKt.mapGraph;

final class InferenceCache {
  private final @NotNull GrControlFlowOwner myScope;
  private final GroovyControlFlow myFlow;
  private final Map<PsiElement, List<Instruction>> myFromByElements;

  private final Lazy<List<DefinitionMap>> myDefinitionMaps;

  private final AtomicReference<Int2ObjectMap<DFAType>>[] myVarTypes;
  private final Set<Instruction> myTooComplexInstructions = ContainerUtil.newConcurrentSet();
  /**
   * Instructions outside any cycle. The control flow graph does not have backward edges on these instructions, so it is safe
   * to assume that DFA visits them <b>only once<b/>.
   */
  private final Lazy<BitSet> simpleInstructions;

  InferenceCache(@NotNull GrControlFlowOwner scope) {
    myScope = scope;
    myFlow = TypeInferenceHelper.getFlatControlFlow(scope);
    myDefinitionMaps = lazyPub(() -> getDefUseMaps(myFlow));
    myFromByElements = Arrays.stream(myFlow.getFlow()).filter(it -> it.getElement() != null).collect(Collectors.groupingBy(Instruction::getElement));
    //noinspection unchecked
    AtomicReference<Int2ObjectMap<DFAType>>[] basicTypes = new AtomicReference[myFlow.getFlow().length];
    for (int i = 0; i < myFlow.getFlow().length; i++) {
        basicTypes[i] = new AtomicReference<>(new Int2ObjectOpenHashMap<>());
    }
    myVarTypes = basicTypes;
    simpleInstructions = lazyPub(() -> getSimpleInstructions(myFlow.getFlow()));
  }

  boolean isTooComplexToAnalyze() {
    return myDefinitionMaps.getValue() == null;
  }

  @Nullable
  PsiType getInferredType(int descriptor,
                          @NotNull Instruction instruction,
                          boolean mixinOnly) {
    if (myTooComplexInstructions.contains(instruction)) return null;

    final List<DefinitionMap> definitionMaps = myDefinitionMaps.getValue();
    if (definitionMaps == null) {
      return null;
    }

    Int2ObjectMap<DFAType> cache = myVarTypes[instruction.num()].get();
    if (descriptor != 0 && !cache.containsKey(descriptor)) {
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
    return dfaType == null ? null : dfaType.getResultType();
  }

  @Nullable
  private List<@Nullable TypeDfaState> performTypeDfa(@NotNull GrControlFlowOwner owner,
                                                      @NotNull GroovyControlFlow flow,
                                                      @NotNull DFAFlowInfo flowInfo) {
    final TypeDfaInstance dfaInstance = new TypeDfaInstance(flow, flowInfo, this, owner.getManager(), new InitialTypeProvider(owner, myFlow.getVarIndices()));
    final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
    return new DFAEngine<>(flow.getFlow(), dfaInstance, semilattice).performDFAWithTimeout();
  }

  @Nullable
  DFAType getCachedInferredType(int descriptorId, @NotNull Instruction instruction) {
    if (descriptorId == 0) {
      return null;
    }
    return myVarTypes[instruction.num()].get().get(descriptorId);
  }

  /**
   * This method helps to reduce number of DFA re-invocations by caching known variable types when it is certain that they won't be changed.
   */
  void publishDescriptor(@NotNull TypeDfaState intermediateState, @NotNull Instruction instruction) {
    if (simpleInstructions.getValue().get(instruction.num()) && TypeInferenceHelper.getCurrentContext() == TypeInferenceHelper.getTopContext()) {
      myVarTypes[instruction.num()].getAndUpdate(oldState -> TypesSemilattice.mergeForCaching(oldState, intermediateState));
    }
  }

  private DFAFlowInfo collectFlowInfo(@NotNull List<DefinitionMap> definitionMaps,
                                      @NotNull Instruction instruction,
                                      int descriptorId,
                                      @NotNull Predicate<? super Instruction> predicate) {
    Map<Pair<Instruction, Integer>, Collection<Pair<Instruction, Integer>>> interesting = new LinkedHashMap<>();
    LinkedList<Pair<Instruction, Integer>> queue = new LinkedList<>();
    queue.add(Pair.create(instruction, descriptorId));

    while (!queue.isEmpty()) {
      Pair<Instruction, Integer> pair = queue.removeFirst();
      if (!interesting.containsKey(pair)) {
        Set<Pair<Instruction, Integer>> dependencies = findDependencies(definitionMaps, pair.first, pair.second);
        interesting.put(pair, dependencies);
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
    Set<Integer> interestingDescriptors = interesting.keySet().stream()
      .map(it -> it.getSecond())
      .collect(Collectors.toSet());
    return new DFAFlowInfo(interestingInstructions,
                           acyclicInstructions,
                           interestingDescriptors);
  }

  @NotNull
  private Set<Pair<Instruction, Integer>> findDependencies(@NotNull List<DefinitionMap> definitionMaps,
                                                                      @NotNull Instruction instruction,
                                                                      int descriptorId) {
    DefinitionMap definitionMap = definitionMaps.get(instruction.num());
    IntSet definitions = definitionMap.getDefinitions(descriptorId);

    LinkedHashSet<Pair<Instruction, Integer>> pairs = new LinkedHashSet<>();

    if (definitions == null) return pairs;

    for (int defIndex : definitions) {
      Instruction write = myFlow.getFlow()[defIndex];
      if (write != instruction) {
        pairs.add(Pair.create(write, descriptorId));
      }
      for (ReadWriteVariableInstruction dependency : findReadDependencies(write, it -> myFromByElements.getOrDefault(it, emptyList()))) {
        pairs.add(Pair.create(dependency, dependency.getDescriptor()));
      }
    }
    return pairs;
  }

  private void cacheDfaResult(@NotNull List<@Nullable TypeDfaState> dfaResult,
                              Set<Instruction> storingInstructions) {
    for (var instruction : storingInstructions) {
      int index = instruction.num();
      myVarTypes[index].getAndUpdate(oldState -> TypesSemilattice.mergeForCaching(oldState, dfaResult.get(index)));
    }
  }

  public DefinitionMap getDefinitionMaps(int instructionNum) {
    return myDefinitionMaps.getValue().get(instructionNum);
  }

  GroovyControlFlow getGroovyFlow() {
    return myFlow;
  }

}
