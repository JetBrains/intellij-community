package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxOnInvalidateClause;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxOnInvalidateClauseImpl extends JavaFxBaseElementImpl implements JavaFxOnInvalidateClause {
  public JavaFxOnInvalidateClauseImpl(@NotNull ASTNode node) {
    super(node);
  }
}
