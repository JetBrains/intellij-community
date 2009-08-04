package org.jetbrains.plugins.groovy.codeInspection.noReturnMethod;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowUtil;

/**
 * @author ven
 */
public class MissingReturnInspection extends LocalInspectionTool {
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
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0 || !(statements[statements.length - 1] instanceof GrExpression)) {
      final boolean hasReturns = hasReturnStatements(block);
      if (!hasReturns && mustReturnValue || hasReturns && !ControlFlowUtil.alwaysReturns(block.getControlFlow())) {
        addNoReturnMessage(block, holder);
      }
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
}
