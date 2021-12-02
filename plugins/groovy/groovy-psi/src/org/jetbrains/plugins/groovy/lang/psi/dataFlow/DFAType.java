// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.NegatingGotoInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * {@link DFAType} is an immutable wrapper for {@link PsiType} of a {@link org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor}
 * used in Groovy Type DFA.
 * <p>
 * This class heavily relies on referential equality, so be cautious while create new instances of it. Prefer using <b>with*</b> methods,
 * that avoid unnecessary allocations.
 */
public final class DFAType {

  private static final DFAType NULL_DFA_TYPE = new DFAType(null, PsiType.NULL);

  private final @Nullable PsiType primary;

  /**
   * Flushing type is required for handling consequences of assignments inside closures
   * with unknown {@link org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind}.
   * It is possible that such closures will be invoked in multithreaded environment, so every
   * reassigned descriptor from these closures will have two types:
   * the primary one, that appears in usual flow, and the flushing one,
   * that is a LUB of all assignments in a dangling closure.
   * <p>
   * Actual type of the variable is a LUB of primary and flushing type.
   */
  private final @NotNull PsiType flushingType;

  private final List<Mixin> mixins = new ArrayList<>();

  private DFAType(@Nullable PsiType primary, @NotNull PsiType flushingType) {
    this.primary = primary;
    this.flushingType = flushingType;
  }

  @Contract(pure = true)
  @NotNull
  public DFAType withNewMixin(@Nullable PsiType mixin, @Nullable ConditionInstruction instruction) {
    if (mixin == null || mixin == PsiType.NULL) {
      return this;
    }
    Mixin newMixin = new Mixin(mixin, instruction, instruction != null && instruction.isNegated());
    for (var existingMixin : mixins) {
      if (Objects.equals(existingMixin, newMixin)) {
        return this;
      }
    }
    DFAType newDfaType = new DFAType(this.primary, this.flushingType);
    newDfaType.mixins.add(newMixin);
    return newDfaType;
  }

  @Contract(pure = true)
  @NotNull
  public DFAType withFlushingType(@Nullable PsiType flushingType, @NotNull PsiManager manager) {
    if (flushingType == null) {
      return this;
    }
    PsiType newFlushingType = GenericsUtil.getLeastUpperBound(this.flushingType, flushingType, manager);
    if (newFlushingType == this.flushingType) {
      return this;
    }
    DFAType newDFAType = create(primary, newFlushingType);
    newDFAType.mixins.addAll(mixins);
    return newDFAType;
  }

  @Contract(pure = true)
  @NotNull
  public DFAType withNegated(@NotNull NegatingGotoInstruction negation) {
    if (mixins.isEmpty()) {
      return this;
    }
    final Set<ConditionInstruction> conditionsToNegate = negation.getCondition().getDependentConditions();
    if (ContainerUtil.and(mixins, mixin -> !conditionsToNegate.contains(mixin.myCondition))) {
      return this;
    }
    DFAType result = copy();
    for (ListIterator<Mixin> iterator = result.mixins.listIterator(); iterator.hasNext(); ) {
      Mixin mixin = iterator.next();
      if (conditionsToNegate.contains(mixin.myCondition)) {
        iterator.set(mixin.negate());
      }
    }
    return result;
  }

  @Contract(pure = true)
  @NotNull
  public static DFAType merge(DFAType t1, DFAType t2, PsiManager manager) {
    if (t1.equals(t2)) return t1;
    if (dominates(t1, t2)) {
      return t1;
    }
    if (dominates(t2, t1)) {
      return t2;
    }
    final PsiType primary = TypesUtil.getLeastUpperBoundNullable(t1.primary, t2.primary, manager);
    final PsiType commonFlushingType = GenericsUtil.getLeastUpperBound(t1.flushingType, t2.flushingType, manager);
    final PsiType type1 = reduce(t1.mixins);
    final PsiType type2 = reduce(t2.mixins);
    if (type1 != null && type2 != null) {
      return create(primary, commonFlushingType).withNewMixin(GenericsUtil.getLeastUpperBound(type1, type2, manager), null);
    }
    return create(primary, commonFlushingType);
  }

  public @NotNull PsiType getFlushingType() {
    return flushingType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DFAType)) return false;

    final DFAType other = (DFAType)obj;

    if (!eq(primary, other.primary)) return false;

    if (mixins.size() != other.mixins.size()) return false;
    for (Mixin mixin1 : mixins) {
      boolean contains = false;
      for (Mixin mixin2 : other.mixins) {
        if (mixin1.equals(mixin2)) {
          contains = mixin1.myNegated == mixin2.myNegated;
          break;
        }
      }
      if (!contains) return false;
    }

    return true;
  }

  @Nullable
  public PsiType getResultType(@NotNull PsiManager manager) {
    PsiType flushedPrimary = GenericsUtil.getLeastUpperBound(primary, flushingType, manager);
    if (mixins.isEmpty()) return flushedPrimary;

    List<PsiType> types = new ArrayList<>();
    if (flushedPrimary != null) {
      types.add(flushedPrimary);
    }
    for (Mixin mixin : mixins) {
      if (mixin.myNegated) {
        continue;
      }
      if (mixin.myType.equals(PsiType.NULL)) {
        continue;
      }
      types.add(mixin.myType);
    }
    if (types.isEmpty()) return null;
    return PsiIntersectionType.createIntersection(types.toArray(PsiType.createArray(types.size())));
  }

  @NotNull
  public static DFAType create(@Nullable PsiType type, @Nullable PsiType flushingType) {
    var actualFlushingType = flushingType == null ? PsiType.NULL : flushingType;
    return type == null && actualFlushingType == PsiType.NULL ? NULL_DFA_TYPE : new DFAType(type, actualFlushingType);
  }

  private static boolean eq(PsiType t1, PsiType t2) {
    return t1 == t2 || Comparing.equal(TypeConversionUtil.erasure(t1), TypeConversionUtil.erasure(t2));
  }

  /**
   * Fast check that allows to determine if one dfa type contains not less information than the other.
   */
  private static boolean dominates(DFAType dominating, DFAType dominated) {
    boolean primaryDominating = dominated.primary == null || dominated.primary == PsiType.NULL || dominated.primary == dominating.primary;
    if (!primaryDominating) return false;
    boolean flushingDominating = dominated.flushingType == PsiType.NULL || dominated.flushingType == dominating.flushingType;
    if (!flushingDominating) return false;
    return dominated.mixins.isEmpty() || dominated.mixins == dominating.mixins;
  }

  private static PsiType reduce(List<Mixin> mixins) {
    List<PsiType> types = mixins.stream()
      .filter(it -> !it.myNegated)
      .map(it -> it.myType)
      .collect(toList());
    return types.isEmpty() ? null : PsiIntersectionType.createIntersection(types);
  }

  @Contract("-> new")
  @NotNull
  private DFAType copy() {
    final DFAType type = new DFAType(primary, flushingType);
    type.mixins.addAll(mixins);
    return type;
  }

  @Override
  public String toString() {
    return "{" + primary + " : " + mixins + " | " + flushingType + "}";
  }

  private static final class Mixin {

    private final @NotNull PsiType myType;
    private final @Nullable ConditionInstruction myCondition;
    private final boolean myNegated;

    private Mixin(@NotNull PsiType type, @Nullable ConditionInstruction condition, boolean negated) {
      myType = type;
      myCondition = condition;
      myNegated = negated;
    }

    private Mixin negate() {
      return new Mixin(myType, myCondition, !myNegated);
    }

    @Override
    public String toString() {
      return (myNegated ? "!" : "") + myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Mixin mixin = (Mixin)o;

      if (!myType.equals(mixin.myType)) return false;
      if (!Objects.equals(myCondition, mixin.myCondition)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType.hashCode();
      result = 31 * result + (myCondition != null ? myCondition.hashCode() : 0);
      return result;
    }
  }

}
