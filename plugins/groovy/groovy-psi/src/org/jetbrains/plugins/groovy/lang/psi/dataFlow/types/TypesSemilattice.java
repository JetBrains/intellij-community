// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;
  private final Map<VariableDescriptor, Integer> varIndexes;

  private final TypeDfaState initialState;

  public TypesSemilattice(@NotNull PsiManager manager, @NotNull TypeDfaState initialState, Map<VariableDescriptor, Integer> varIndexes) {
    myManager = manager;
    this.initialState = initialState;
    this.varIndexes = varIndexes;
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
      result.joinState(ins.get(i), myManager, varIndexes);
    }
    return result;
  }

  @Override
  public boolean eq(@NotNull TypeDfaState e1, @NotNull TypeDfaState e2) {
    return e1.contentsEqual(e2);
  }

  public static Map<VariableDescriptor, DFAType> mergeForCaching(Map<VariableDescriptor, DFAType> cached,
                                                                 TypeDfaState another,
                                                                 Map<VariableDescriptor, Integer> varIndexes) {
    if (another.getVarTypes().isEmpty()) {
      return cached;
    }
    Map<VariableDescriptor, DFAType> mapToPublish = getMapToPublish(another, varIndexes);
    checkDfaStatesConsistency(cached, mapToPublish);
    Map<VariableDescriptor, DFAType> newState = new HashMap<>(cached);
    newState.putAll(mapToPublish);
    return newState;
  }

  private static Map<VariableDescriptor, DFAType> getMapToPublish(TypeDfaState another,
                                                                  Map<VariableDescriptor, Integer> varIndexes) {
    return filter(another.getVarTypes(), descriptor -> !another.getProhibitedCachingVars().get(varIndexes.getOrDefault(descriptor, 0)));
  }

  private static void checkDfaStatesConsistency(@NotNull Map<VariableDescriptor, DFAType> cached,
                                                @NotNull Map<VariableDescriptor, DFAType> incoming) {
    if (!ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManagerEx.isInStressTest() ||
        DfaCacheConsistencyKt.mustSkipConsistencyCheck()) {
      return;
    }
    Collection<VariableDescriptor> commonDescriptors = intersection(cached.keySet(), incoming.keySet());
    Map<VariableDescriptor, Couple<DFAType>> differingEntries = filter(diff(cached, incoming), commonDescriptors::contains);
    if (!differingEntries.isEmpty()) {
      throw new IllegalStateException("Attempt to cache different types: " + differingEntries);
    }
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
  private final BitSet myProhibitedCachingVars;

  TypeDfaState() {
    myVarTypes = new HashMap<>();
    myProhibitedCachingVars = new BitSet();
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = new HashMap<>(another.myVarTypes);
    myProhibitedCachingVars = BitSet.valueOf(another.myProhibitedCachingVars.toLongArray());
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
    myProhibitedCachingVars.or(another.myProhibitedCachingVars);
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
    return myVarTypes.toString();
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
