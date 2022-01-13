// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * State of flow typing in Groovy Type DFA.
 * <p>
 * This class is immutable. Its usage heavily relies on referential equality, so be cautious while creating new instances.
 */
class TypeDfaState {

  private static final Logger LOG = Logger.getInstance(TypeDfaState.class);

  static final TypeDfaState EMPTY_STATE = new TypeDfaState(new Int2ObjectOpenHashMap<>(), new BitSet(), null);

  /**
   * Mapping of variable descriptor IDs to their types. The mapping of descriptors to IDs resides in {@link InferenceCache#myVarIndexes}.
   */
  private final Int2ObjectMap<DFAType> myVarTypes;

  private final @Nullable FList<ClosureFrame> myPreviousClosureState;

  /**
   * During the DFA process, types of some descriptors become inferred.
   * In the presense of cyclic instructions, these inferred types may become incorrect:
   * a variable may be overwritten at some non-interesting write instruction, and then it would affect the flow before this write.
   * This scenario requires erasing descriptor types at non-interesting write instruction,
   * but the information about erased descriptors should be memoized somewhere --
   * otherwise, semilattice may "restore" erased type while joining state, and then the further flow will be unaffected.
   * This is why we need this field:
   * it should carry information about erased types to distinguish them from not-yet-processed ones.
   */
  private final BitSet myProhibitedCachingVars;

  private TypeDfaState(Int2ObjectMap<DFAType> varTypes, BitSet prohibitedCachingVars, @Nullable FList<ClosureFrame> frame) {
    myVarTypes = varTypes;
    myProhibitedCachingVars = prohibitedCachingVars;
    myPreviousClosureState = frame;
  }

  @Contract(pure = true)
  @NotNull
  public static TypeDfaState merge(@NotNull TypeDfaState left, @NotNull TypeDfaState right, PsiManager manager) {
    if (left == right) {
      return left;
    }
    Int2ObjectMap<DFAType> dominantMap = guessDominantMap(left.myVarTypes, right.myVarTypes);
    if (dominates(left, right, dominantMap)) {
      return left;
    }
    if (dominates(right, left, dominantMap)) {
      return right;
    }
    BitSet resultSet = mergeProhibitedVariables(left.myProhibitedCachingVars, right.myProhibitedCachingVars);
    Int2ObjectMap<DFAType> resultMap = dominantMap != null ? dominantMap : mergeTypeMaps(left.myVarTypes, right.myVarTypes, manager, resultSet);
    FList<ClosureFrame> frame = left.myPreviousClosureState == null ? right.myPreviousClosureState : left.myPreviousClosureState;
    return new TypeDfaState(resultMap, resultSet, frame);
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withNewType(int variableIndex, @NotNull DFAType type) {
    BitSet newSet;
    if (variableIndex == 0 || !myProhibitedCachingVars.get(variableIndex)) {
      newSet = myProhibitedCachingVars;
    }
    else {
      newSet = (BitSet)myProhibitedCachingVars.clone();
      newSet.set(variableIndex, false);
    }
    Int2ObjectMap<DFAType> newTypes;
    if (myVarTypes.get(variableIndex) == type) {
      newTypes = myVarTypes;
    }
    else {
      newTypes = new Int2ObjectOpenHashMap<>(myVarTypes);
      newTypes.put(variableIndex, type);
    }
    if (newSet == myProhibitedCachingVars && newTypes == myVarTypes) {
      return this;
    }
    else {
      return new TypeDfaState(newTypes, newSet, myPreviousClosureState);
    }
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withRemovedBinding(int variableIndex) {
    if (variableIndex == 0 || myProhibitedCachingVars.get(variableIndex)) {
      return this;
    }
    BitSet newProhibitedVars = (BitSet)myProhibitedCachingVars.clone();
    newProhibitedVars.set(variableIndex, true);
    return new TypeDfaState(myVarTypes, newProhibitedVars, myPreviousClosureState);
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withNewClosureState(@NotNull ClosureFrame frame) {
    if (myPreviousClosureState != null && frame == myPreviousClosureState.getHead()) {
      return this;
    }
    else {
      FList<ClosureFrame> frames = myPreviousClosureState == null ? FList.emptyList() : myPreviousClosureState;
      return new TypeDfaState(myVarTypes, myProhibitedCachingVars, frames.prepend(frame));
    }
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withoutTopClosureState() {
    LOG.assertTrue(myPreviousClosureState != null && !myPreviousClosureState.isEmpty(), "Reached closure end without closure start");
    FList<ClosureFrame> tail = myPreviousClosureState.getTail();
    return new TypeDfaState(myVarTypes, myProhibitedCachingVars, tail.isEmpty() ? null : tail);
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withNewMap(@NotNull Int2ObjectMap<DFAType> types) {
    if (Objects.equals(types, myVarTypes)) {
      return this;
    }
    else {
      return new TypeDfaState(types, myProhibitedCachingVars, myPreviousClosureState);
    }
  }

  @Contract(pure = true)
  @NotNull
  public TypeDfaState withRemovedBindings(BitSet newBindings) {
    if (this.myProhibitedCachingVars.equals(newBindings)) {
      return this;
    }
    else {
      return new TypeDfaState(this.myVarTypes, newBindings, this.myPreviousClosureState);
    }
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes) &&
           myProhibitedCachingVars.equals(another.myProhibitedCachingVars) &&
           another.myPreviousClosureState == myPreviousClosureState;
  }

  @Nullable
  DFAType getVariableType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    int index = varIndexes.getOrDefault(descriptor, 0);
    return index != 0 && !myProhibitedCachingVars.get(index) ? myVarTypes.get(index) : null;
  }

  public BitSet getRemovedBindings() {
    return myProhibitedCachingVars;
  }

  Int2ObjectMap<DFAType> getRawVarTypes() {
    return myVarTypes;
  }

  @Contract(pure = true)
  @NotNull
  DFAType getNotNullDFAType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    DFAType result = getVariableType(descriptor, varIndexes);
    return result == null ? DFAType.NULL_DFA_TYPE : result;
  }

  @NotNull ClosureFrame getTopClosureFrame() {
    LOG.assertTrue(myPreviousClosureState != null && !myPreviousClosureState.isEmpty(), "Reached closure end without closure start");
    return myPreviousClosureState.getHead();
  }

  @Override
  @NonNls
  public String toString() {
    String evicted = myProhibitedCachingVars.isEmpty() ? "" : ", (caching prohibited: " + myProhibitedCachingVars + ")";
    String frame = (myPreviousClosureState == null || myPreviousClosureState.isEmpty()) ? "" : ", frame size: " + myPreviousClosureState.size();
    return myVarTypes.toString() + evicted + frame;
  }

  public boolean containsVariable(int varIndex) {
    return myVarTypes.containsKey(varIndex);
  }

  boolean isProhibited(int index) {
    return (index == 0 || myProhibitedCachingVars.get(index));
  }

  private static BitSet mergeProhibitedVariables(BitSet leftSet, BitSet rightSet) {
    if (leftSet.equals(rightSet)) {
      return leftSet;
    }
    BitSet prohibited = ((BitSet)leftSet.clone());
    prohibited.or(rightSet);
    return prohibited;
  }

  @NotNull
  private static Int2ObjectMap<DFAType> mergeTypeMaps(Int2ObjectMap<DFAType> leftMap,
                                                      Int2ObjectMap<DFAType> rightMap,
                                                      PsiManager manager,
                                                      BitSet prohibited) {
    Int2ObjectMap<DFAType> newFMap = new Int2ObjectOpenHashMap<>();
    Stream.concat(rightMap.int2ObjectEntrySet().stream(), leftMap.int2ObjectEntrySet().stream()).forEach(entry -> {
      int descriptorId = entry.getIntKey();
      if (descriptorId == 0 || prohibited.get(descriptorId)) {
        return;
      }
      DFAType candidate = entry.getValue();
      DFAType existing = newFMap.get(descriptorId);
      if (existing == null) {
        newFMap.put(descriptorId, candidate);
      }
      else if (candidate != existing) {
        newFMap.put(descriptorId, DFAType.merge(candidate, existing, manager));
      }
    });
    return newFMap;
  }

  private static @Nullable Int2ObjectMap<DFAType> guessDominantMap(Int2ObjectMap<DFAType> left,
                                                                   Int2ObjectMap<DFAType> right) {
    if (left.size() == right.size() && dominatesAll(left, right)) {
      return left;
    }
    else if (left.size() > right.size() && dominatesAll(left, right)) {
      return left;
    }
    else if (left.size() < right.size() && dominatesAll(right, left)) {
      return right;
    }
    else {
      return null;
    }
  }

  private static boolean dominatesAll(Int2ObjectMap<DFAType> dominator, Int2ObjectMap<DFAType> dominated) {
    for (Int2ObjectMap.Entry<DFAType> dominatedEntry : dominated.int2ObjectEntrySet()) {
      DFAType dominatingType = dominator.get(dominatedEntry.getIntKey());
      if (dominatingType == null || !DFAType.dominates(dominatingType, dominatedEntry.getValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tries to guess if one dfa state contains more information than the other.
   */
  private static boolean dominates(TypeDfaState dominator, TypeDfaState dominated, Int2ObjectMap<DFAType> dominantMap) {
    boolean dominateByTypes = dominated.myVarTypes.isEmpty() || dominantMap == dominator.myVarTypes;
    if (!dominateByTypes) return false;
    boolean dominateByMask = dominator.myProhibitedCachingVars.equals(dominated.myProhibitedCachingVars);
    if (!dominateByMask) return false;
    return dominator.myPreviousClosureState == dominated.myPreviousClosureState ||
           dominated.myPreviousClosureState == null ||
           dominated.myPreviousClosureState.isEmpty();
  }
}

