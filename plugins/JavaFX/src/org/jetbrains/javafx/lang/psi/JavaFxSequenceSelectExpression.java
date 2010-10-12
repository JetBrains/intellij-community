package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:02:23
 */
public interface JavaFxSequenceSelectExpression extends JavaFxValueExpression {
  @NotNull
  JavaFxExpression getOperand();
}
