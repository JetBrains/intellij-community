package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxCatchClause extends JavaFxElement {
  @Nullable
  JavaFxParameter getParameter();
}
