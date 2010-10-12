package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxOnReplaceClause;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxOnReplaceClauseImpl extends JavaFxBaseElementImpl implements JavaFxOnReplaceClause {
  public JavaFxOnReplaceClauseImpl(@NotNull ASTNode node) {
    super(node);
  }
}
