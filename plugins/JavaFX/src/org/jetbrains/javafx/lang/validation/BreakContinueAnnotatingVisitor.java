package org.jetbrains.javafx.lang.validation;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.psi.JavaFxBreakExpression;
import org.jetbrains.javafx.lang.psi.JavaFxContinueExpression;
import org.jetbrains.javafx.lang.psi.JavaFxLoopExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class BreakContinueAnnotatingVisitor extends  JavaFxAnnotatingVisitor {
  @Override
  public void visitBreakExpression(JavaFxBreakExpression node) {
    final JavaFxLoopExpression loopExpression = PsiTreeUtil.getParentOfType(node, JavaFxLoopExpression.class);
    if (loopExpression == null) {
      markError(node, JavaFxBundle.message("break.outside.loop"));
    }
  }

  @Override
  public void visitContinueExpression(JavaFxContinueExpression node) {
    final JavaFxLoopExpression loopExpression = PsiTreeUtil.getParentOfType(node, JavaFxLoopExpression.class);
    if (loopExpression == null) {
      markError(node, JavaFxBundle.message("continue.outside.loop"));
    }
  }
}
