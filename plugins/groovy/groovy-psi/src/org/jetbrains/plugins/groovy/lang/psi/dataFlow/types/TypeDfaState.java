// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;

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
  private final Set<VariableDescriptor> myProhibitedCachingVars;

  TypeDfaState() {
    myVarTypes = new HashMap<>();
    myProhibitedCachingVars = new HashSet<>();
    myPreviousClosureState = FList.emptyList();
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = new HashMap<>(another.myVarTypes);
    myProhibitedCachingVars = new HashSet<>(another.myProhibitedCachingVars);
    myPreviousClosureState = another.myPreviousClosureState;
  }

  Map<VariableDescriptor, DFAType> getVarTypes() {
    return myVarTypes;
  }

  TypeDfaState mergeWith(TypeDfaState another) {
    if (another.myVarTypes.isEmpty()) {
      return this;
    }
    checkDfaStatesConsistency(this, another);
    TypeDfaState state = new TypeDfaState(this);
    Map<VariableDescriptor, DFAType> retainedDescriptors =
      filter(another.myVarTypes, descriptor -> !another.myProhibitedCachingVars.contains(descriptor));
    state.myVarTypes.putAll(retainedDescriptors);
    return state;
  }

  private static void checkDfaStatesConsistency(@NotNull TypeDfaState state, @NotNull TypeDfaState another) {
    if (!ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManagerEx.isInStressTest() ||
        DfaCacheConsistencyKt.mustSkipConsistencyCheck()) {
      return;
    }
    Map<VariableDescriptor, DFAType> anotherTypes =
      filter(another.myVarTypes, descriptor -> !another.myProhibitedCachingVars.contains(descriptor));
    Collection<VariableDescriptor> commonDescriptors = intersection(state.myVarTypes.keySet(), anotherTypes.keySet());
    Map<VariableDescriptor, Couple<DFAType>> differingEntries = filter(diff(state.myVarTypes, anotherTypes), commonDescriptors::contains);
    if (!differingEntries.isEmpty()) {
      throw new IllegalStateException("Attempt to cache different types: " + differingEntries.toString());
    }
  }

  void joinState(TypeDfaState another, PsiManager manager) {
    myVarTypes.keySet().removeAll(another.myProhibitedCachingVars);
    for (Map.Entry<VariableDescriptor, DFAType> entry : another.myVarTypes.entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      if (myProhibitedCachingVars.contains(descriptor)) {
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
    myProhibitedCachingVars.addAll(another.myProhibitedCachingVars);
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
      assert topFrame != null;
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

  public void removeBinding(@NotNull VariableDescriptor descriptor) {
    myProhibitedCachingVars.add(descriptor);
    myVarTypes.remove(descriptor);
  }

  public void restoreBinding(@NotNull VariableDescriptor descriptor) {
    myProhibitedCachingVars.remove(descriptor);
  }
}

