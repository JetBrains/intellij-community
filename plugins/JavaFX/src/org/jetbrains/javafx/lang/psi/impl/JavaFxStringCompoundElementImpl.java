package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.JavaFxStringCompoundElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxStringCompoundElementImpl extends JavaFxBaseElementImpl implements JavaFxStringCompoundElement {
  public JavaFxStringCompoundElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public ASTNode[] getStringNodes() {
    return getNode().getChildren(JavaFxTokenTypes.STRINGS);
  }
}
