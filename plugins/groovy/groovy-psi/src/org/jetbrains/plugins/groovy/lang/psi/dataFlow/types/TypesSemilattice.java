// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;

  private final TypeDfaState initialState;

  public TypesSemilattice(@NotNull PsiManager manager, @NotNull TypeDfaState initialState) {
    myManager = manager;
    this.initialState = initialState;
  }

  @Override
  @NotNull
  public TypeDfaState initial() {
    return new TypeDfaState(initialState);
  }

  @NotNull
  @Override
  public TypeDfaState join(@NotNull List<? extends TypeDfaState> ins) {
    if (ins.isEmpty()) return initial();

    TypeDfaState result = new TypeDfaState(ins.get(0));
    if (ins.size() == 1) {
      return result;
    }

    for (int i = 1; i < ins.size(); i++) {
      result.joinState(ins.get(i), myManager);
    }
    return result;
  }

  @Override
  public boolean eq(@NotNull TypeDfaState e1, @NotNull TypeDfaState e2) {
    return e1.contentsEqual(e2);
  }
}

class TypeDfaState {
  private final Map<VariableDescriptor, DFAType> myVarTypes;

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
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = new HashMap<>(another.myVarTypes);
    myProhibitedCachingVars = new HashSet<>(another.myProhibitedCachingVars);
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
        ApplicationInfoImpl.isInStressTest() ||
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
    return myVarTypes.equals(another.myVarTypes) && myProhibitedCachingVars.equals(another.myProhibitedCachingVars);
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
