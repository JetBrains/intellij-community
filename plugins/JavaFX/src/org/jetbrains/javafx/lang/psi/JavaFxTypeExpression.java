package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxTypeExpression extends JavaFxExpression {
  @NotNull
  JavaFxExpression getLeftOperand();

  @NotNull
  IElementType getOperator();

  @Nullable
  JavaFxTypeElement getTypeElement();
}
