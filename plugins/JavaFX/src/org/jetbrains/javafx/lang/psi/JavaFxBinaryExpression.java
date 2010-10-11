package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:02:23
 */
public interface JavaFxBinaryExpression extends JavaFxValueExpression {
  @NotNull
  IElementType getOperator();

  @NotNull
  JavaFxExpression getLeftOperand();

  @Nullable
  JavaFxExpression getRightOperand();
}
