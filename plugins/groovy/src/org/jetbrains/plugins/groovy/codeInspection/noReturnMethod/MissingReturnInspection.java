/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.noReturnMethod;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;

/**
 * @author ven
 */
public class MissingReturnInspection extends GroovySuppressableInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("no.return.display.name");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder problemsHolder, boolean onTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      public void visitClosure(GrClosableBlock closure) {
        check(closure, problemsHolder, false);
        super.visitClosure(closure);
      }

      public void visitMethod(GrMethod method) {
        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          final boolean mustReturnValue = method.getReturnTypeElementGroovy() != null && method.getReturnType() != PsiType.VOID;
          check(block, problemsHolder, mustReturnValue);
        }
        super.visitMethod(method);
      }
    });

  }

  private static void check(GrCodeBlock block, ProblemsHolder holder, boolean mustReturnValue) {
    if ((mustReturnValue || hasReturnStatements(block)) && !alwaysReturns(block.getControlFlow())) {
      addNoReturnMessage(block, holder);
    }
  }

  private static void addNoReturnMessage(GrCodeBlock block, ProblemsHolder holder) {
    final PsiElement lastChild = block.getLastChild();
    if (lastChild == null) return;
    TextRange range = lastChild.getTextRange();
    if (!lastChild.isValid() || !lastChild.isPhysical() || range.getStartOffset() >= range.getEndOffset()) {
      return;
    }
    holder.registerProblem(lastChild, GroovyInspectionBundle.message("no.return.message"));
  }

  private static boolean hasReturnStatements(GrCodeBlock block) {
    class Visitor extends GroovyRecursiveElementVisitor {
      private boolean myFound = false;

      public boolean isFound() {
        return myFound;
      }

      public void visitReturnStatement(GrReturnStatement returnStatement) {
        if (returnStatement.getReturnValue() != null) myFound = true;
      }

      public void visitElement(GroovyPsiElement element) {
        if (!myFound) {
          super.visitElement(element);
        }
      }
    }
    Visitor visitor = new Visitor();
    block.accept(visitor);
    return visitor.isFound();
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyMissingReturnStatement";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public static boolean alwaysReturns(Instruction[] flow) {
    boolean[] visited = new boolean[flow.length];
    return alwaysReturnsInner(flow[flow.length - 1], flow[0], visited);
  }

  private static boolean alwaysReturnsInner(Instruction last, Instruction first, boolean[] visited) {
    if (first == last) return false;
    if (last instanceof MaybeReturnInstruction) {
      return ((MaybeReturnInstruction)last).mayReturnValue();
    }

    final PsiElement element = last.getElement();
    if (element instanceof GrReturnStatement || element instanceof GrThrowStatement || element instanceof GrAssertStatement) {
      return true;
    }

    if (last.getElement() != null) {
      return false;
    }

    visited[last.num()] = true;
    for (Instruction pred : last.allPred()) {
      if (!visited[pred.num()]) {
        if (!alwaysReturnsInner(pred, first, visited)) return false;
      }
    }
    return true;
  }
}
