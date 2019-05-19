// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.UtilKt.findReadDependencies;
import static org.jetbrains.plugins.groovy.util.GraphKt.findNodesOutsideCycles;
import static org.jetbrains.plugins.groovy.util.GraphKt.mapGraph;

class InferenceCache {

  private final GrControlFlowOwner myScope;
  private final Instruction[] myFlow;
  private final Map<PsiElement, List<Instruction>> myFromByElements;

  private final TObjectIntHashMap<VariableDescriptor> myVarIndexes;
  private final List<DefinitionMap> myDefinitions;

  private final AtomicReference<List<TypeDfaState>> myVarTypes;
  private final Set<Instruction> myTooComplexInstructions = ContainerUtil.newConcurrentSet();

  InferenceCache(@NotNull GrControlFlowOwner scope,
                 @NotNull TObjectIntHashMap<VariableDescriptor> varIndexes,
                 @NotNull List<DefinitionMap> definitions) {
    myScope = scope;
    myFlow = scope.getControlFlow();
    myVarIndexes = varIndexes;
    myDefinitions = definitions;
    myFromByElements = Arrays.stream(myFlow).filter(it -> it.getElement() != null).collect(Collectors.groupingBy(Instruction::getElement));
    List<TypeDfaState> noTypes = new ArrayList<>();
    for (int i = 0; i < myFlow.length; i++) {
      noTypes.add(new TypeDfaState());
    }
    myVarTypes = new AtomicReference<>(noTypes);
  }

  @Nullable
  PsiType getInferredType(@NotNull VariableDescriptor descriptor, @NotNull Instruction instruction, boolean mixinOnly) {
    if (myTooComplexInstructions.contains(instruction)) return null;

    TypeDfaState cache = myVarTypes.get().get(instruction.num());
    if (!cache.containsVariable(descriptor)) {
      Predicate<Instruction> mixinPredicate = mixinOnly ? (e) -> e instanceof MixinTypeInstruction : (e) -> true;
      Couple<Set<Instruction>> interesting = collectRequiredInstructions(instruction, descriptor, mixinPredicate);
      List<TypeDfaState> dfaResult = performTypeDfa(myScope, myFlow, interesting);
      if (dfaResult == null) {
        myTooComplexInstructions.addAll(interesting.first);
      }
      else {
        cacheDfaResult(dfaResult);
      }
    }
    DFAType dfaType = getCachedInferredType(descriptor, instruction);
    return dfaType == null ? null : dfaType.getResultType();
  }

  @Nullable
  private List<TypeDfaState> performTypeDfa(@NotNull GrControlFlowOwner owner,
                                            @NotNull Instruction[] flow,
                                            @NotNull Couple<Set<Instruction>> interesting) {
    final TypeDfaInstance dfaInstance = new TypeDfaInstance(flow, interesting, this);
    final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
    return new DFAEngine<>(flow, dfaInstance, semilattice).performDFAWithTimeout();
  }

  @Nullable
  DFAType getCachedInferredType(@NotNull VariableDescriptor descriptor, @NotNull Instruction instruction) {
    return myVarTypes.get().get(instruction.num()).getVariableType(descriptor);
  }

  private Couple<Set<Instruction>> collectRequiredInstructions(@NotNull Instruction instruction,
                                                               @NotNull VariableDescriptor descriptor,
                                                               @NotNull Predicate<? super Instruction> predicate) {
    Map<Pair<Instruction, VariableDescriptor>, Collection<Pair<Instruction, VariableDescriptor>>> interesting = new LinkedHashMap<>();
    LinkedList<Pair<Instruction, VariableDescriptor>> queue = new LinkedList<>();
    queue.add(Pair.create(instruction, descriptor));
    while (!queue.isEmpty()) {
      Pair<Instruction, VariableDescriptor> pair = queue.removeFirst();
      if (!interesting.containsKey(pair)) {
        Set<Pair<Instruction, VariableDescriptor>> dependencies = findDependencies(pair.first, pair.second);
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
    return Couple.of(interestingInstructions, acyclicInstructions);
  }

  @NotNull
  private Set<Pair<Instruction, VariableDescriptor>> findDependencies(@NotNull Instruction instruction,
                                                                      @NotNull VariableDescriptor descriptor) {
    DefinitionMap definitionMap = myDefinitions.get(instruction.num());
    int varIndex = myVarIndexes.get(descriptor);
    int[] definitions = definitionMap.getDefinitions(varIndex);
    if (definitions == null) return Collections.emptySet();

    LinkedHashSet<Pair<Instruction, VariableDescriptor>> pairs = new LinkedHashSet<>();
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

  private void cacheDfaResult(@NotNull List<TypeDfaState> dfaResult) {
    myVarTypes.accumulateAndGet(dfaResult, InferenceCache::addDfaResult);
  }

  @NotNull
  private static List<TypeDfaState> addDfaResult(@NotNull List<TypeDfaState> oldTypes, @NotNull List<TypeDfaState> dfaResult) {
    List<TypeDfaState> newTypes = new ArrayList<>(oldTypes);
    for (int i = 0; i < dfaResult.size(); i++) {
      newTypes.set(i, newTypes.get(i).mergeWith(dfaResult.get(i)));
    }
    return newTypes;
  }
}
