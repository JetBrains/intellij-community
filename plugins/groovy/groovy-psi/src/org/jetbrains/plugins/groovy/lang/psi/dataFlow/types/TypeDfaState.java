// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

class TypeDfaState {
  private final Map<VariableDescriptor, DFAType> myVarTypes;
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
    myVarTypes = new HashMap<>();
    myProhibitedCachingVars = new BitSet();
    myPreviousClosureState = FList.emptyList();
  }

  //private TypeDfaState(TypeDfaState another) {
  //  myVarTypes = another.myVarTypes;
  //  myProhibitedCachingVars = BitSet.valueOf(another.myProhibitedCachingVars.toLongArray());
  //  myPreviousClosureState = another.myPreviousClosureState;
  //}

  private TypeDfaState(Map<VariableDescriptor, DFAType> varTypes, BitSet prohibitedCachingVars, FList<ClosureFrame> frame) {
    myVarTypes = varTypes;
    myProhibitedCachingVars = prohibitedCachingVars;
    myPreviousClosureState = frame;
  }

  Map<VariableDescriptor, DFAType> getRawVarTypes() {
    return myVarTypes;
  }


  public TypeDfaState withMerged(TypeDfaState another, PsiManager manager, Map<VariableDescriptor, Integer> varIndexes) {
    if (this == another) {
      return this;
    }
    boolean mapEqual = this.myVarTypes.equals(another.myVarTypes);
    if (dominates(this, another, mapEqual)) {
      return this;
    }
    if (dominates(another, this, mapEqual)) {
      return another;
    }

    BitSet prohibited =
      myProhibitedCachingVars == another.myProhibitedCachingVars ? myProhibitedCachingVars : ((BitSet)myProhibitedCachingVars.clone());
    if (prohibited != myProhibitedCachingVars) {
      prohibited.or(another.myProhibitedCachingVars);
    }
    Map<VariableDescriptor, DFAType> newMap = getDominantMap(this.myVarTypes, another.myVarTypes, mapEqual);
    if (newMap == null) {
      Map<VariableDescriptor, DFAType> newFMap = new HashMap<>();
      Stream.concat(another.myVarTypes.entrySet().stream(), this.myVarTypes.entrySet().stream()).forEach(entry -> {
        VariableDescriptor descriptor = entry.getKey();
        int index = ((Object2IntMap<VariableDescriptor>)varIndexes).getInt(descriptor);
        if (index == 0 || prohibited.get(index)) {
          return;
        }
        DFAType candidate = entry.getValue();
        DFAType existing = newFMap.get(descriptor);
        if (existing == null) {
          newFMap.put(descriptor, candidate);
        }
        else if (candidate != existing) {
          newFMap.put(descriptor, DFAType.create(candidate, existing, manager));
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

  private static @Nullable Map<VariableDescriptor, DFAType> getDominantMap(Map<VariableDescriptor, DFAType> left,
                                                                           Map<VariableDescriptor, DFAType> right,
                                                                           boolean mapEqual) {
    if (left == right) {
      return left;
    }
    if (left.size() == right.size()) {
      if (mapEqual) {
        return left;
      }
      else {
        return null;
      }
    }
    else if (left.size() < right.size()) {
      if (isMapDominated(left, right)) {
        return right;
      }
      else {
        return null;
      }
    }
    else {
      if (isMapDominated(right, left)) {
        return left;
      }
      else {
        return null;
      }
    }
  }

  private static boolean isMapDominated(Map<VariableDescriptor, DFAType> left, Map<VariableDescriptor, DFAType> right) {
    boolean rightDominating = true;
    for (VariableDescriptor leftDescriptor : left.keySet()) {
      if (!left.get(leftDescriptor).equals(right.get(leftDescriptor))) {
        rightDominating = false;
        break;
      }
    }
    return rightDominating;
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes) &&
           myProhibitedCachingVars.equals(another.myProhibitedCachingVars) &&
           another.myPreviousClosureState == myPreviousClosureState;
  }

  @Nullable
  DFAType getVariableType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    int index = varIndexes.getOrDefault(descriptor, 0);
    return index == 0 || !myProhibitedCachingVars.get(index) ? myVarTypes.get(descriptor) : null;
  }

  @Contract("_, _ -> new")
  @NotNull
  DFAType getOrCreateVariableType(VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    DFAType result = getVariableType(descriptor, varIndexes);
    return result == null ? DFAType.create(null, PsiType.NULL) : result;
  }

  private void putType(VariableDescriptor descriptor, @Nullable DFAType type) {
    myVarTypes.put(descriptor, type);
    if (type != null && !myPreviousClosureState.isEmpty()) {
      var topFrame = myPreviousClosureState.getHead();
      if (topFrame.getStartState().containsVariable(descriptor)) {
        topFrame.addReassignment(descriptor, type);
      }
    }
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

  public boolean containsVariable(@NotNull VariableDescriptor descriptor) {
    return myVarTypes.containsKey(descriptor);
  }

  public void removeBinding(@NotNull VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    myProhibitedCachingVars.set(varIndexes.getOrDefault(descriptor, 0));
    myVarTypes.remove(descriptor);
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
  public TypeDfaState withNewType(@NotNull VariableDescriptor descriptor, DFAType type, Map<VariableDescriptor, Integer> varIndexes) {
    int index = ((Object2IntMap<VariableDescriptor>)varIndexes).getInt(descriptor);
    BitSet newSet;
    if (index == 0 || !myProhibitedCachingVars.get(index)) {
      newSet = myProhibitedCachingVars;
    }
    else {
      newSet = (BitSet)myProhibitedCachingVars.clone();
      newSet.set(index, false);
    }
    Map<VariableDescriptor, DFAType> newTypes;
    if (myVarTypes.get(descriptor) == type) {
      newTypes = myVarTypes;
    }
    else {
      newTypes = new HashMap<>(myVarTypes);
      newTypes.put(descriptor, type);
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
  public TypeDfaState withNewMap(@NotNull Map<VariableDescriptor, DFAType> types) {
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

