// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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

class TypeDfaState {
  private final Int2ObjectMap<DFAType> myVarTypes;
  private FList<ClosureFrame> myPreviousClosureState; // todo: special state for UNINITIALIZED for catching faults

  /**
   * During the DFA process, types of some descriptors become inferred.
   * In the presense of cyclic instructions, these inferred types may become incorrect:
   * a variable may be overwritten at some non-interesting write instruction, and then it would affect the flow before this write.
   * This scenario requires to erase descriptor types at non-interesting write instruction,
   * but the information about erased descriptors should be memoized somewhere --
   * otherwise, semilattice may "restore" erased type while joining state, and then the further flow will be unaffected.
   * This is why we need this field:
   * it should carry information about erased types to distinguish them from not-yet-processed ones.
   */
  private final BitSet myProhibitedCachingVars;

  TypeDfaState() {
    myVarTypes = new Int2ObjectOpenHashMap<>();
    myProhibitedCachingVars = new BitSet();
    myPreviousClosureState = FList.emptyList();
  }

  private TypeDfaState(Int2ObjectMap<DFAType> varTypes, BitSet prohibitedCachingVars, FList<ClosureFrame> frame) {
    myVarTypes = varTypes;
    myProhibitedCachingVars = prohibitedCachingVars;
    myPreviousClosureState = frame;
  }

  Int2ObjectMap<DFAType> getRawVarTypes() {
    return myVarTypes;
  }


  public TypeDfaState withMerged(TypeDfaState another, PsiManager manager, Map<VariableDescriptor, Integer> varIndexes) {
    if (this == another) {
      return this;
    }
    boolean eq = this.myVarTypes.size() == another.myVarTypes.size() && this.myVarTypes.int2ObjectEntrySet().containsAll(another.myVarTypes.int2ObjectEntrySet());
    boolean lcr = this.myVarTypes.size() > another.myVarTypes.size() && this.myVarTypes.int2ObjectEntrySet().containsAll(another.myVarTypes.int2ObjectEntrySet());
    boolean rcl = another.myVarTypes.size() > this.myVarTypes.size() && another.myVarTypes.int2ObjectEntrySet().containsAll(this.myVarTypes.int2ObjectEntrySet());
    if (dominates(this, another, eq || lcr)) {
      return this;
    }
    if (dominates(another, this, eq || rcl)) {
      return another;
    }

    BitSet prohibited =
      myProhibitedCachingVars == another.myProhibitedCachingVars ? myProhibitedCachingVars : ((BitSet)myProhibitedCachingVars.clone());
    if (prohibited != myProhibitedCachingVars) {
      prohibited.or(another.myProhibitedCachingVars);
    }
    Int2ObjectMap<DFAType> newMap = getDominantMap(this.myVarTypes, another.myVarTypes, eq || lcr, eq || rcl);
    if (newMap == null) {
      Int2ObjectMap<DFAType> newFMap = new Int2ObjectOpenHashMap<>();
      Stream.concat(another.myVarTypes.int2ObjectEntrySet().stream(), this.myVarTypes.int2ObjectEntrySet().stream()).forEach(entry -> {
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
          newFMap.put(descriptorId, DFAType.create(candidate, existing, manager));
        }
        // todo: flushings
      });
      newMap = newFMap;
    }
    FList<ClosureFrame> frame = this.myPreviousClosureState.isEmpty() ? another.myPreviousClosureState : myPreviousClosureState;
    return new TypeDfaState(newMap, prohibited, frame);
  }

  private static boolean dominates(TypeDfaState dominator, TypeDfaState dominated, boolean mapEqual) {
    boolean dominateByTypes = dominated.myVarTypes.isEmpty() || mapEqual;
    if (!dominateByTypes) return false;
    boolean dominateByMask = dominator.myProhibitedCachingVars.equals(dominated.myProhibitedCachingVars);
    if (!dominateByMask) return false;
    return dominator.myPreviousClosureState == dominated.myPreviousClosureState || dominated.myPreviousClosureState.isEmpty();
  }

  private static @Nullable Int2ObjectMap<DFAType> getDominantMap(Int2ObjectMap<DFAType> left,
                                                                           Int2ObjectMap<DFAType> right,
                                                                           boolean leftContainsRight,
                                                                           boolean rightContainsLeft) {
    if (left == right) {
      return left;
    }
    if (leftContainsRight) {
      return left;
    }
    if (rightContainsLeft) {
      return right;
    }
    return null;
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes) &&
           myProhibitedCachingVars.equals(another.myProhibitedCachingVars) &&
           another.myPreviousClosureState == myPreviousClosureState;
  }

  @Nullable
  DFAType getVariableType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    int index = varIndexes.getOrDefault(descriptor, 0);
    return index == 0 || !myProhibitedCachingVars.get(index) ? myVarTypes.get(index) : null;
  }

  @Contract("_, _ -> new")
  @NotNull
  DFAType getOrCreateVariableType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    DFAType result = getVariableType(descriptor, varIndexes);
    return result == null ? DFAType.create(null, PsiType.NULL) : result;
  }

  @Nullable ClosureFrame popTopClosureFrame() {
    var head = myPreviousClosureState.getHead();
    myPreviousClosureState = myPreviousClosureState.getTail();
    return head;
  }

  private boolean shouldBeIgnored(@NotNull VariableDescriptor descriptor, @NotNull Map<VariableDescriptor, Integer> map) {
    int id = ((Object2IntMap<VariableDescriptor>)map).getInt(descriptor);
    return id == 0 || myProhibitedCachingVars.get(id);
  }

  @Override
  @NonNls
  public String toString() {
    String evicted = myProhibitedCachingVars.isEmpty() ? "" : ", (caching prohibited: " + myProhibitedCachingVars + ")";
    String frame = myPreviousClosureState.isEmpty() ? "" : ", frame size: " + myPreviousClosureState.size();
    return myVarTypes.toString() + evicted + frame;
  }

  public boolean containsVariable(int varIndex) {
    return myVarTypes.containsKey(varIndex);
  }

  @Contract("_, _ -> new")
  public TypeDfaState withRemovedBinding(@NotNull VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    if (shouldBeIgnored(descriptor, varIndexes)) {
      return this;
    }
    BitSet newProhibitedVars = (BitSet)myProhibitedCachingVars.clone();
    newProhibitedVars.set(varIndexes.getOrDefault(descriptor, 0), true);
    return new TypeDfaState(myVarTypes, newProhibitedVars, myPreviousClosureState);
  }

  @Contract("_, _, _ -> new")
  public TypeDfaState withNewType(int index, DFAType type, Map<VariableDescriptor, Integer> varIndexes) {
    BitSet newSet;
    if (index == 0 || !myProhibitedCachingVars.get(index)) {
      newSet = myProhibitedCachingVars;
    }
    else {
      newSet = (BitSet)myProhibitedCachingVars.clone();
      newSet.set(index, false);
    }
    Int2ObjectMap<DFAType> newTypes;
    if (myVarTypes.get(index) == type) {
      newTypes = myVarTypes;
    }
    else {
      newTypes = new Int2ObjectOpenHashMap<>(myVarTypes);
      newTypes.put(index, type);
    }
    if (newSet == myProhibitedCachingVars && newTypes == myVarTypes) {
      return this;
    }
    else {
      return new TypeDfaState(newTypes, newSet, myPreviousClosureState);
    }
  }

  @Contract("_ -> new")
  public TypeDfaState withNewClosureState(@NotNull TypeDfaState state) {
    return new TypeDfaState(myVarTypes, myProhibitedCachingVars, myPreviousClosureState.prepend(new ClosureFrame(state)));
  }

  @Contract(pure = true)
  public TypeDfaState withNewMap(@NotNull Int2ObjectMap<DFAType> types) {
    if (Objects.equals(types, myVarTypes)) {
      return this;
    }
    else {
      return new TypeDfaState(types, myProhibitedCachingVars, myPreviousClosureState);
    }
  }

  BitSet getProhibitedCachingVars() {
    return myProhibitedCachingVars;
  }
}

