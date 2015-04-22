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
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;
import org.jetbrains.plugins.groovy.lang.flow.GrNullness;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrNullabilityInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Set;

public class GrNullabilityInspection extends GroovySuppressableInspectionTool {

  public boolean UNKNOWN_MEMBERS_ARE_NULLABLE = false;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder problemsHolder, boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(new MyVisitor(problemsHolder, isOnTheFly));
  }

  private static class MyVisitor extends GroovyElementVisitor {
    private final ProblemsHolder myProblemsHolder;
    private final boolean myIsOnTheFly;

    private MyVisitor(ProblemsHolder problemsHolder, boolean onTheFly) {
      myProblemsHolder = problemsHolder;
      myIsOnTheFly = onTheFly;
    }

    @Override
    public void visitMethod(GrMethod method) {
      final GrOpenBlock block = method.getBlock();
      if (block != null) {
        check(block, myProblemsHolder, myIsOnTheFly);
      }
    }
  }

  private static void check(@NotNull GrControlFlowOwner owner, @NotNull ProblemsHolder holder, final boolean onTheFly) {
    final GrDataFlowRunner<GrNullabilityInstructionVisitor> dfaRunner = new GrDataFlowRunner<GrNullabilityInstructionVisitor>() {
      @Override
      protected boolean shouldCheckTimeLimit() {
        if (!onTheFly) return false;
        return super.shouldCheckTimeLimit();
      }
    };
    final GrNullabilityInstructionVisitor visitor = new GrNullabilityInstructionVisitor(dfaRunner);
    final RunnerResult rc = dfaRunner.analyzeMethod(owner, visitor);
    if (rc == RunnerResult.OK) {
      final Set<PsiElement> alreadyReported = ContainerUtil.newHashSet();

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.callNPE)) {
        if (!alreadyReported.add(element)) continue;
        holder.registerProblem(element, InspectionsBundle.message("dataflow.message.npe.method.invocation"));
      }

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.fieldAccessNPE)) {
        if (!alreadyReported.add(element)) continue;
        final String text = InspectionsBundle.message("dataflow.message.npe.field.access");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.passingNullableToNotNullParameter)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                            : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.assigningToNotNull)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.assigning.null")
                            : InspectionsBundle.message("dataflow.message.assigning.nullable");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.nullableReturn)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull")
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter)) {
        if (alreadyReported.contains(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? "Passing <code>null</code> argument to non annotated parameter"
                            : "Argument <code>#ref</code> #loc might be null but passed to non annotated parameter";
        holder.registerProblem(element, text);
      }

      final Pair<Set<Instruction>, Set<Instruction>> constConditionalExpressions = dfaRunner.getConstConditionalExpressions();
      for (Instruction instruction : constConditionalExpressions.first) {
        if (instruction instanceof ConditionalGotoInstruction) {
          final PsiElement element = ((ConditionalGotoInstruction)instruction).getPsiAnchor();
          final boolean negated = ((ConditionalGotoInstruction)instruction).isNegated();
          holder.registerProblem(element, "Condition <code>#ref</code> is always " + (negated ? "false" : "true"));
        }
      }
      for (Instruction instruction : constConditionalExpressions.second) {
        if (instruction instanceof ConditionalGotoInstruction) {
          final PsiElement element = ((ConditionalGotoInstruction)instruction).getPsiAnchor();
          final boolean negated = ((ConditionalGotoInstruction)instruction).isNegated();
          holder.registerProblem(element, "Condition <code>#ref</code> is always " + (negated ? "true" : "false"));
        }
      }
    }
    else if (rc == RunnerResult.TOO_COMPLEX) {
      final PsiElement parent = owner.getParent();
      if (parent instanceof GrMethod) {
        final PsiElement identifierGroovy = ((GrMethod)parent).getNameIdentifierGroovy();
        holder.registerProblem(identifierGroovy, InspectionsBundle.message("dataflow.too.complex"), ProblemHighlightType.WEAK_WARNING);
      }
    }
  }

  @SuppressWarnings("unused")
  private class MyVisitor2 extends GroovyRecursiveElementVisitor {

    private final ProblemsHolder myProblemsHolder;

    private MyVisitor2(ProblemsHolder problemsHolder) {
      this.myProblemsHolder = problemsHolder;
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression expression) {
      final GrExpression rValue = expression.getRValue();
      if (rValue == null) return;
      final Nullness left = GrNullness.getNullability(expression.getLValue());
      final Nullness right = GrNullness.getNullability(rValue);
      if (left == Nullness.NOT_NULL && (right == Nullness.NULLABLE || right == Nullness.UNKNOWN && UNKNOWN_MEMBERS_ARE_NULLABLE)) {
        final String text = GrInspectionUtil.isNull(rValue)
                            ? InspectionsBundle.message("dataflow.message.assigning.null")
                            : InspectionsBundle.message("dataflow.message.assigning.nullable");
        myProblemsHolder.registerProblem(rValue, text);
      }
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      super.visitReferenceExpression(referenceExpression);
    }

    @Override
    public void visitExpression(GrExpression expression) {
      super.visitExpression(expression);
    }
  }
}
