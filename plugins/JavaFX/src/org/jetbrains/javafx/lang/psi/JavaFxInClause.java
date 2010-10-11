package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxInClause extends JavaFxElement {
  JavaFxInClause[] EMPTY_ARRAY = new JavaFxInClause[0];

  @NotNull
  JavaFxParameter getParameter();

  @Nullable
  JavaFxExpression getIteratedExpression();
}
