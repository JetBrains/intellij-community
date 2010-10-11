package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:43:22
 */
public interface JavaFxAssignmentExpression extends JavaFxValueExpression {
  @Nullable
  JavaFxExpression getAssignedValue();
}
