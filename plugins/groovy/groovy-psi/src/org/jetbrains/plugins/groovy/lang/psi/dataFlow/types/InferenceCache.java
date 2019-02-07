// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement;
import static org.jetbrains.plugins.groovy.util.GraphKt.findNodesOutsideCycles;
import static org.jetbrains.plugins.groovy.util.GraphKt.mapGraph;

class InferenceCache {

  private final GrControlFlowOwner myScope;
  private final Instruction[] myFlow;
  private final Map<PsiElement, List<Instruction>> myFromByElements;

  private final TObjectIntHashMap<String> myVarIndexes;
  private final List<DefinitionMap> myDefinitions;

  private final AtomicReference<List<TypeDfaState>> myVarTypes;
  private final Set<Instruction> myTooComplexInstructions = ContainerUtil.newConcurrentSet();

  InferenceCache(@NotNull GrControlFlowOwner scope,
                 @NotNull TObjectIntHashMap<String> varIndexes,
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
  PsiType getInferredType(@NotNull String variableName, @NotNull Instruction instruction, boolean mixinOnly) {
    if (myTooComplexInstructions.contains(instruction)) return null;

    TypeDfaState cache = myVarTypes.get().get(instruction.num());
    if (!cache.containsVariable(variableName)) {
      Predicate<Instruction> mixinPredicate = mixinOnly ? (e) -> e instanceof MixinTypeInstruction : (e) -> true;
      Couple<Set<Instruction>> interesting = collectRequiredInstructions(instruction, variableName, mixinPredicate);
      List<TypeDfaState> dfaResult = performTypeDfa(myScope, myFlow, interesting);
      if (dfaResult == null) {
        myTooComplexInstructions.addAll(interesting.first);
      }
      else {
        cacheDfaResult(dfaResult);
      }
    }
    DFAType dfaType = getCachedInferredType(variableName, instruction);
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
  DFAType getCachedInferredType(@NotNull String variableName, @NotNull Instruction instruction) {
    DFAType dfaType = myVarTypes.get().get(instruction.num()).getVariableType(variableName);
    return dfaType == null ? null : dfaType.negate(instruction);
  }

  private Couple<Set<Instruction>> collectRequiredInstructions(@NotNull Instruction instruction,
                                                               @NotNull String variableName,
                                                               @NotNull Predicate<? super Instruction> predicate) {
    Map<Pair<Instruction, String>, Collection<Pair<Instruction, String>>> interesting = new LinkedHashMap<>();
    LinkedList<Pair<Instruction, String>> queue = ContainerUtil.newLinkedList();
    queue.add(Pair.create(instruction, variableName));
    while (!queue.isEmpty()) {
      Pair<Instruction, String> pair = queue.removeFirst();
      if (!interesting.containsKey(pair)) {
        Set<Pair<Instruction, String>> dependencies = findDependencies(pair.first, pair.second);
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
  private Set<Pair<Instruction, String>> findDependencies(@NotNull Instruction instruction, @NotNull String varName) {
    DefinitionMap definitionMap = myDefinitions.get(instruction.num());
    int varIndex = myVarIndexes.get(varName);
    int[] definitions = definitionMap.getDefinitions(varIndex);
    if (definitions == null) return Collections.emptySet();

    LinkedHashSet<Pair<Instruction, String>> pairs = ContainerUtil.newLinkedHashSet();
    for (int defIndex : definitions) {
      Instruction write = myFlow[defIndex];
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
          List<Instruction> instructionList = myFromByElements.get(element);
          if (varName != null && instructionList != null) {
            for (Instruction dependency : instructionList) {
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
    return PsiTreeUtil.findFirstParent(
      element,
      element1 -> !(element1.getParent() instanceof GrExpression)
                  || element1 instanceof GrInstanceOfExpression
                  || isExpressionStatement(element1)
    );
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
