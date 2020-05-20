// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;

  public TypesSemilattice(PsiManager manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public TypeDfaState initial() {
    return new TypeDfaState();
  }

  @NotNull
  @Override
  public TypeDfaState join(@NotNull List<? extends TypeDfaState> ins) {
    if (ins.isEmpty()) return new TypeDfaState();

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

  TypeDfaState() {
    myVarTypes = new HashMap<>();
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = new HashMap<>(another.myVarTypes);
  }

  Map<VariableDescriptor, DFAType> getVarTypes() {
    return myVarTypes;
  }

  TypeDfaState mergeWith(TypeDfaState another) {
    if (another.myVarTypes.isEmpty()) {
      return this;
    }
    TypeDfaState state = new TypeDfaState(this);
    state.myVarTypes.putAll(another.myVarTypes);
    return state;
  }

  void joinState(TypeDfaState another, PsiManager manager) {
    for (Map.Entry<VariableDescriptor, DFAType> entry : another.myVarTypes.entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      final DFAType t1 = entry.getValue();
      if (myVarTypes.containsKey(descriptor)) {
        final DFAType t2 = myVarTypes.get(descriptor);
        if (t1 != null && t2 != null) {
          myVarTypes.put(descriptor, DFAType.create(t1, t2, manager));
        }
        else {
          myVarTypes.put(descriptor, null);
        }
      } else if (t1 != null && t1.getFlushingType() != null && !t1.getFlushingType().equals(PsiType.NULL)) {
        DFAType dfaType = DFAType.create(null);
        myVarTypes.put(descriptor, dfaType.addFlushingType(t1.getFlushingType(), manager));
      }
    }
  }

  boolean contentsEqual(TypeDfaState another) {
    return myVarTypes.equals(another.myVarTypes);
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
  public String toString() {
    return myVarTypes.toString();
  }

  public boolean containsVariable(@NotNull VariableDescriptor descriptor) {
    return myVarTypes.containsKey(descriptor);
  }

  public void removeBinding(VariableDescriptor descriptor) {
    myVarTypes.remove(descriptor);
  }
}
