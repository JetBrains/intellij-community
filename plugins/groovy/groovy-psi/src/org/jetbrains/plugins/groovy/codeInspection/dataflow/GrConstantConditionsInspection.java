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
import org.jetbrains.plugins.groovy.lang.flow.visitor.GrNullabilityInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.flow.visitor.GrStandardInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.flow.visitor.GrNullabilityProblem.*;

public class GrConstantConditionsInspection extends GroovySuppressableInspectionTool {

  public boolean UNKNOWN_MEMBERS_ARE_NULLABLE = false;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder problemsHolder, boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(new MyVisitor(problemsHolder, isOnTheFly));
  }

  private class MyVisitor extends GroovyElementVisitor {
    private final ProblemsHolder myProblemsHolder;
    private final boolean myIsOnTheFly;

    private MyVisitor(ProblemsHolder problemsHolder, boolean onTheFly) {
      myProblemsHolder = problemsHolder;
      myIsOnTheFly = onTheFly;
    }

    @Override
    public void visitMethod(GrMethod method) {
      check(method, myProblemsHolder, myIsOnTheFly, UNKNOWN_MEMBERS_ARE_NULLABLE);
    }

    @Override
    public void visitFile(GroovyFileBase file) {
      if (file.isScript()) {
        check(file, myProblemsHolder, myIsOnTheFly, UNKNOWN_MEMBERS_ARE_NULLABLE);
      }
    }
    
    @Override
    public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
      for (GrMembersDeclaration declaration : typeDefinitionBody.getMemberDeclarations()) {
        if (declaration instanceof GrVariableDeclaration) {
          check(declaration, myProblemsHolder, myIsOnTheFly, UNKNOWN_MEMBERS_ARE_NULLABLE);
        }
      }
    }

    @Override
    public void visitClassInitializer(GrClassInitializer initializer) {
      check(initializer, myProblemsHolder, myIsOnTheFly, UNKNOWN_MEMBERS_ARE_NULLABLE);
    }
  }

  private static void check(@NotNull PsiElement owner, @NotNull ProblemsHolder holder, final boolean onTheFly, boolean unknownAreNullable) {
    final GrDataFlowRunner<GrStandardInstructionVisitor> dfaRunner =
      new GrDataFlowRunner<GrStandardInstructionVisitor>(owner.getProject(), unknownAreNullable) {
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

      for (final PsiElement element : visitor.getProblems(callNPE)) {
        if (!alreadyReported.add(element)) continue;
        holder.registerProblem(element, InspectionsBundle.message("dataflow.message.npe.method.invocation"));
      }

      for (final PsiElement element : visitor.getProblems(fieldAccessNPE)) {
        if (!alreadyReported.add(element)) continue;
        final String text = InspectionsBundle.message("dataflow.message.npe.field.access");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(passingNullableToNotNullParameter)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                            : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(assigningToNotNull)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.assigning.null")
                            : InspectionsBundle.message("dataflow.message.assigning.nullable");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(nullableReturn)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull")
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull");
        holder.registerProblem(element, text);
      }

      for (final PsiElement element : visitor.getProblems(passingNullableArgumentToNonAnnotatedParameter)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? "Passing <code>null</code> argument to non annotated parameter"
                            : "Argument <code>#ref</code> #loc might be null but passed to non annotated parameter";
        holder.registerProblem(element, text);
      }

      for (PsiElement element : visitor.getProblems(passingNullableAsRangeBound)) {
        if (!alreadyReported.add(element)) continue;
        final String text = GrInspectionUtil.isNull(element)
                            ? "Passing <code>null</code> bound"
                            : "Bound <code>#ref</code> #loc might be null";
        holder.registerProblem(element, text);
      }

      for (PsiElement element : visitor.getProblems(unboxingNullable)) {
        if (!alreadyReported.add(element)) continue;
        holder.registerProblem(element, InspectionsBundle.message("dataflow.message.unboxing"));
      }

      final Pair<Set<Instruction>, Set<Instruction>> constConditionalExpressions = dfaRunner.getConstConditionalExpressions();
      for (Instruction instruction : constConditionalExpressions.first) {
        if (instruction instanceof ConditionalGotoInstruction) {
          processCondition(holder, (ConditionalGotoInstruction)instruction, true);
        }
      }
      for (Instruction instruction : constConditionalExpressions.second) {
        if (instruction instanceof ConditionalGotoInstruction) {
          processCondition(holder, (ConditionalGotoInstruction)instruction, false);
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

  private static void processCondition(@NotNull ProblemsHolder holder, @NotNull ConditionalGotoInstruction instruction, boolean isTrue) {
    final PsiElement element = instruction.getPsiAnchor();
    if (element == null) return;
    final PsiElement parent = element.getParent();
    final String text;
    if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getQualifier() == element) {
      assert ((GrReferenceExpression)parent).getDotTokenType() == GroovyTokenTypes.mOPTIONAL_DOT;
      text = String.format("Qualifier <code>#ref</code> is always %s", isTrue ? "not null" : "null");
    }
    else {
      text = String.format("Condition <code>#ref</code> is always %b", isTrue);
    }
    holder.registerProblem(element, text);
  }
}
