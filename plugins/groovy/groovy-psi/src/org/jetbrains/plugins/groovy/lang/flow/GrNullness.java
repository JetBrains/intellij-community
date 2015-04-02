package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrGenericStandardInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

import java.util.Collections;
import java.util.Set;

public class GrNullness {
  private final static Couple<Set<PsiElement>> EMPTY = Couple.of(Collections.<PsiElement>emptySet(), Collections.<PsiElement>emptySet());

  @NotNull
  public static Nullness getNullability(@Nullable PsiElement element) {
    if (element instanceof GrMember) {
      boolean notNull = NullableNotNullManager.isNotNull((PsiModifierListOwner)element);
      if (notNull) return Nullness.NOT_NULL;
      boolean nullable = NullableNotNullManager.isNullable((PsiModifierListOwner)element);
      if (nullable) return Nullness.NULLABLE;
      return Nullness.UNKNOWN;
    }
    else if (element == null) {
      return Nullness.UNKNOWN;
    }
    else {
      final Couple<Set<PsiElement>> inner = getNullabilityInner(element);
      final Set<PsiElement> notNulls = inner.first;
      final Set<PsiElement> nulls = inner.second;
      if (nulls.contains(element)) {
        return Nullness.NULLABLE;
      }
      else if (notNulls.contains(element)) {
        return Nullness.NOT_NULL;
      }
      else {
        return Nullness.UNKNOWN;
      }
    }
  }

  private static Couple<Set<PsiElement>> getNullabilityInner(@NotNull PsiElement element) {
    final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(element);
    if (flowOwner == null) {
      return EMPTY;
    }

    final GrDataFlowRunner<GrNullabilityCollector> dfaRunner = new GrDataFlowRunner<GrNullabilityCollector>();
    final GrNullabilityCollector collector = new GrNullabilityCollector(dfaRunner);
    dfaRunner.analyzeMethod(flowOwner, collector);
    return Couple.of(collector.notNulls, collector.nulls);
  }


  private static class GrNullabilityCollector extends GrGenericStandardInstructionVisitor<GrNullabilityCollector> {
    private final Set<PsiElement> notNulls = ContainerUtil.newHashSet();
    private final Set<PsiElement> nulls = ContainerUtil.newHashSet();

    public GrNullabilityCollector(GrDataFlowRunner<GrNullabilityCollector> runner) {
      super(runner);
    }

    @Override
    public void markNotNull(PsiElement element) {
      notNulls.add(element);
    }

    @Override
    public void markNull(PsiElement element) {
      nulls.add(element);
    }
  }
}
