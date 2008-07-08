package org.jetbrains.plugins.groovy.codeInspection.noReturnMethod;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author ven
 */
public class MissingReturnInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
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
        check(closure, problemsHolder);
        super.visitClosure(closure);
      }

      public void visitMethod(GrMethod method) {
        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          check(block, problemsHolder);
        }
        super.visitMethod(method);
      }
    });

  }

  private void check(GrCodeBlock block, ProblemsHolder holder) {
    GrStatement[] statements = block.getStatements();
    if (statements.length > 0 && !(statements[statements.length - 1] instanceof GrExpression)) {
      if (hasValueReturns(block)) {
        Instruction[] flow = block.getControlFlow();
        if (!ControlFlowUtil.alwaysReturns(flow)) {
          final PsiElement lastChild = block.getLastChild();
          if (lastChild == null) {
            return;
          }
          holder.registerProblem(lastChild, GroovyInspectionBundle.message("no.return.message"));
        }
      }
    }
  }

  private boolean hasValueReturns(GrCodeBlock block) {
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
}
