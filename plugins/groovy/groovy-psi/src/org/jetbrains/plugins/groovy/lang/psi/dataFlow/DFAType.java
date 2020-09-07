// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.NegatingGotoInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author Max Medvedev
 */
public final class DFAType {

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

  private final PsiType primary;

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

  private DFAType(@Nullable PsiType primary) {
    this.primary = primary;
    flushingType = PsiType.NULL;
  }

  private DFAType(@Nullable PsiType primary, @Nullable PsiType flushingType) {
    this.primary = primary;
    this.flushingType = flushingType == null ? PsiType.NULL : flushingType;
  }

  public void addMixin(@Nullable PsiType mixin, @Nullable ConditionInstruction instruction) {
    if (mixin == null) {
      return;
    }

    mixins.add(new Mixin(mixin, instruction, instruction != null && instruction.isNegated()));
  }

  public DFAType addFlushingType(@Nullable PsiType flushingType, @NotNull PsiManager manager) {
    PsiType newFlushingType = GenericsUtil.getLeastUpperBound(this.flushingType, flushingType, manager);
    DFAType newDFAType = new DFAType(primary, newFlushingType);
    newDFAType.mixins.addAll(mixins);
    return newDFAType;
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

  @Contract("-> new")
  @NotNull
  public DFAType copy() {
    final DFAType type = new DFAType(primary, flushingType);
    type.mixins.addAll(mixins);
    return type;
  }

  @Contract("_ -> new")
  @NotNull
  public DFAType negate(@NotNull NegatingGotoInstruction negation) {
    DFAType result = copy();
    final Set<ConditionInstruction> conditionsToNegate = negation.getCondition().getDependentConditions();
    for (ListIterator<Mixin> iterator = result.mixins.listIterator(); iterator.hasNext(); ) {
      Mixin mixin = iterator.next();
      if (conditionsToNegate.contains(mixin.myCondition)) {
        iterator.set(mixin.negate());
      }
    }
    return result;
  }

  @Nullable
  public PsiType getResultType(@NotNull PsiManager manager) {
    PsiType flushedPrimary = PsiType.NULL.equals(flushingType) ? primary : GenericsUtil.getLeastUpperBound(primary, flushingType, manager);
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

  @Contract("_ -> new")
  @NotNull
  public static DFAType create(@Nullable PsiType type) {
    return new DFAType(type);
  }

  private static boolean eq(PsiType t1, PsiType t2) {
    return t1 == t2 || Comparing.equal(TypeConversionUtil.erasure(t1), TypeConversionUtil.erasure(t2));
  }

  @NotNull
  public static DFAType create(DFAType t1, DFAType t2, PsiManager manager) {
    if (t1.equals(t2)) return t1;

    final PsiType primary = TypesUtil.getLeastUpperBoundNullable(t1.primary, t2.primary, manager);
    final PsiType commonFlushingType = GenericsUtil.getLeastUpperBound(t1.flushingType, t2.flushingType, manager);
    final DFAType type = new DFAType(primary, commonFlushingType);
    final PsiType type1 = reduce(t1.mixins);
    final PsiType type2 = reduce(t2.mixins);
    if (type1 != null && type2 != null) {
      type.addMixin(GenericsUtil.getLeastUpperBound(type1, type2, manager), null);
    }

    return type;
  }

  private static PsiType reduce(List<Mixin> mixins) {
    List<PsiType> types = mixins.stream()
      .filter(it -> !it.myNegated)
      .map(it -> it.myType)
      .collect(toList());
    return types.isEmpty() ? null : PsiIntersectionType.createIntersection(types);
  }

  @Override
  public String toString() {
    return "{" + primary + " : " + mixins + "}";
  }
}
