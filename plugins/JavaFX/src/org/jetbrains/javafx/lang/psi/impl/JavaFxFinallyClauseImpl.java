package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxFinallyClause;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFinallyClauseImpl extends JavaFxBaseElementImpl implements JavaFxFinallyClause {
  public JavaFxFinallyClauseImpl(@NotNull final ASTNode node) {
    super(node);
  }
}
