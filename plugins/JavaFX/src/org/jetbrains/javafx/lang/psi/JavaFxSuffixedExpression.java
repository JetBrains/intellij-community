package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:43:22
 */
public interface JavaFxSuffixedExpression extends JavaFxValueExpression {
  @NotNull
  IElementType getOperator();

  @NotNull
  JavaFxExpression getOperand();
}
