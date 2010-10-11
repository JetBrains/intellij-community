package org.jetbrains.javafx.run;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class RunnableScriptUtil {
  private RunnableScriptUtil() {
  }

  public static boolean isRunnable(@NotNull final PsiFile script) {
    final Visitor visitor = new Visitor();
    script.acceptChildren(visitor);
    return visitor.myResult;
  }

  private static class Visitor extends JavaFxElementVisitor {
    private boolean myResult;

    @Override
    public void visitFunctionDefinition(JavaFxFunctionDefinition node) {
      super.visitFunctionDefinition(node);
      if ("run".equals(node.getName())) {
        myResult = true;
      }
    }

    @Override
    public void visitObjectLiteral(JavaFxObjectLiteral node) {
      super.visitObjectLiteral(node);
      final String nodeName = node.getName();
      if ("Stage".equals(nodeName) || "javafx.stage.Stage".equals(nodeName)) {
        myResult = true;
      }
    }

    // TODO: check type
    @Override
    public void visitVariableDeclaration(JavaFxVariableDeclaration node) {
      super.visitVariableDeclaration(node);
      final JavaFxExpression initializer = node.getInitializer();
      if (initializer != null) {
        initializer.accept(this);
      }
    }

    @Override
    public void visitAssignmentExpression(JavaFxAssignmentExpression node) {
      super.visitAssignmentExpression(node);
      final JavaFxExpression assignedValue = node.getAssignedValue();
      if (assignedValue != null) {
        assignedValue.accept(this);
      }
    }
  }
}
