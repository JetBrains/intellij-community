/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.NegatingGotoInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class DFAType {
  private static class Mixin {
    private final int ID;

    private final PsiType myType;
    private final ConditionInstruction myCondition;
    private final boolean myNegated;

    private Mixin(PsiType type, ConditionInstruction condition, boolean negated) {
      this(-1, type, condition, negated);
    }

    private Mixin(int ID, PsiType type, ConditionInstruction condition, boolean negated) {
      if (ID == -1) ID = hashCode();
      this.ID = ID;
      myType = type;
      myCondition = condition;
      myNegated = negated;
    }

    Mixin negate() {
      return new Mixin(ID, myType, myCondition, !myNegated);
    }

    @Override
    public String toString() {
      return "Mixin{" +
             "ID=" + ID +
             ", myType=" + myType +
             ", myCondition=" + myCondition +
             ", myNegated=" + myNegated +
             '}';
    }
  }

  private final PsiType primary;

  private final List<Mixin> mixins = new ArrayList<>();

  private DFAType(@Nullable PsiType primary) {
    this.primary = primary;
  }

  public void addMixin(@Nullable PsiType mixin, ConditionInstruction instruction) {
    if (mixin == null) {
      return;
    }

    mixins.add(new Mixin(mixin, instruction, false));
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
        if (mixin1.ID == mixin2.ID) {
          contains = mixin1.myNegated == mixin2.myNegated;
          break;
        }
      }
      if (!contains) return false;
    }

    return true;
  }

  public DFAType negate(@NotNull Instruction instruction) {
    final DFAType type = new DFAType(primary);

    for (Mixin mixin : mixins) {
      type.mixins.add(mixin);
    }

    for (NegatingGotoInstruction negation: instruction.getNegatingGotoInstruction()) {
      final Set<ConditionInstruction> conditionsToNegate = negation.getCondition().getDependentConditions();

      for (ListIterator<Mixin> iterator = type.mixins.listIterator(); iterator.hasNext(); ) {
        Mixin mixin = iterator.next();
        if (conditionsToNegate.contains(mixin.myCondition)) {
          iterator.set(mixin.negate());
        }
      }
    }
    return type;
  }

  @Nullable
  public PsiType getResultType() {
    if (mixins.isEmpty()) return primary;

    List<PsiType> types = new ArrayList<>();
    if (primary != null) {
      types.add(primary);
    }
    for (Mixin mixin : mixins) {
      if (!mixin.myNegated) {
        types.add(mixin.myType);
      }
    }
    if (types.isEmpty()) return null;
    return PsiIntersectionType.createIntersection(types.toArray(PsiType.createArray(types.size())));
  }

  public static DFAType create(@Nullable PsiType type) {
    return new DFAType(type);
  }

  private static boolean eq(PsiType t1, PsiType t2) {
    return t1 == t2 || Comparing.equal(TypeConversionUtil.erasure(t1), TypeConversionUtil.erasure(t2));
  }

  @Nullable
  public static DFAType create(DFAType t1, DFAType t2, PsiManager manager) {
    if (t1.equals(t2)) return t1;

    final PsiType primary = TypesUtil.getLeastUpperBoundNullable(t1.primary, t2.primary, manager);
    final DFAType type = new DFAType(primary);

    for (Mixin mixin1 : t1.mixins) {
      for (Mixin mixin2 : t2.mixins) {
        if (mixin1.ID == mixin2.ID && mixin1.myNegated == mixin2.myNegated) {
          type.mixins.add(mixin1);
        }
      }
    }
    return type;
  }

  @Override
  public String toString() {
    return "DFAType{" +
           "primary=" + primary +
           ", mixins=" + mixins +
           '}';
  }
}
