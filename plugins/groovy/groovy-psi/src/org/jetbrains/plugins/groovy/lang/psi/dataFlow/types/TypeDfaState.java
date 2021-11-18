// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

class TypeDfaState {
  private final Map<VariableDescriptor, DFAType> myVarTypes;
  private FList<ClosureFrame> myPreviousClosureState;

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

  TypeDfaState(TypeDfaState another) {
    myVarTypes = new HashMap<>(another.myVarTypes);
    myProhibitedCachingVars = BitSet.valueOf(another.myProhibitedCachingVars.toLongArray());
    myPreviousClosureState = another.myPreviousClosureState;
  }

  Map<VariableDescriptor, DFAType> getVarTypes() {
    return myVarTypes;
  }

  void joinState(TypeDfaState another, PsiManager manager, Map<VariableDescriptor, Integer> varIndexes) {
    myVarTypes.keySet().removeIf(var -> another.myProhibitedCachingVars.get(varIndexes.get(var)));
    for (Map.Entry<VariableDescriptor, DFAType> entry : another.myVarTypes.entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      if (myProhibitedCachingVars.get(varIndexes.getOrDefault(descriptor, 0))) {
        continue;
      }
      final DFAType t1 = entry.getValue();
      if (myVarTypes.containsKey(descriptor)) {
        final DFAType t2 = myVarTypes.get(descriptor);
        if (t1 != null && t2 != null) {
          myVarTypes.put(descriptor, DFAType.create(t1, t2, manager));
        }
        else {
          myVarTypes.put(descriptor, null);
        }
      }
      else if (t1 != null && !t1.getFlushingType().equals(PsiType.NULL)) {
        DFAType dfaType = DFAType.create(null);
        myVarTypes.put(descriptor, dfaType.addFlushingType(t1.getFlushingType(), manager));
      }
    }
    if (!another.myPreviousClosureState.isEmpty()) {
      this.myPreviousClosureState = another.myPreviousClosureState;
    }
    myProhibitedCachingVars.or(another.myProhibitedCachingVars);
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes) &&
           myProhibitedCachingVars.equals(another.myProhibitedCachingVars) &&
           another.myPreviousClosureState == myPreviousClosureState;
  }

  @Nullable
  DFAType getVariableType(VariableDescriptor descriptor) {
    return myVarTypes.get(descriptor);
  }

  @Contract("_ -> new")
  @NotNull
  DFAType getOrCreateVariableType(VariableDescriptor descriptor) {
    DFAType result = getVariableType(descriptor);
    return result == null ? DFAType.create(null) : result.copy();
  }

  Map<VariableDescriptor, DFAType> getBindings() {
    return new HashMap<>(myVarTypes);
  }

  void putType(VariableDescriptor descriptor, @Nullable DFAType type) {
    myVarTypes.put(descriptor, type);
    if (type != null && !myPreviousClosureState.isEmpty()) {
      var topFrame = myPreviousClosureState.getHead();
      if (topFrame.getStartState().containsVariable(descriptor)) {
        topFrame.addReassignment(descriptor, type);
      }
    }
  }

  void addClosureState(@NotNull TypeDfaState state) {
    myPreviousClosureState = myPreviousClosureState.prepend(new ClosureFrame(state));
  }

  @Nullable ClosureFrame popTopClosureFrame() {
    var head = myPreviousClosureState.getHead();
    myPreviousClosureState = myPreviousClosureState.getTail();
    return head;
  }

  @Override
  @NonNls
  public String toString() {
    String evicted = myProhibitedCachingVars.isEmpty() ? "" : " (caching prohibited: " + myProhibitedCachingVars.toString() + ")";
    return myVarTypes.toString() + evicted;
  }

  public boolean containsVariable(@NotNull VariableDescriptor descriptor) {
    return myVarTypes.containsKey(descriptor);
  }

  public void removeBinding(@NotNull VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    myProhibitedCachingVars.set(varIndexes.getOrDefault(descriptor, 0));
    myVarTypes.remove(descriptor);
  }

  BitSet getProhibitedCachingVars() {
    return myProhibitedCachingVars;
  }

  public void restoreBinding(@NotNull VariableDescriptor descriptor, Map<VariableDescriptor, Integer> varIndexes) {
    myProhibitedCachingVars.set(varIndexes.getOrDefault(descriptor, 0), false);
  }
}

