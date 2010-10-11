package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxInitBlock;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPostinitBlockImpl extends JavaFxBaseElementImpl implements JavaFxInitBlock {
  public JavaFxPostinitBlockImpl(@NotNull ASTNode node) {
    super(node);
  }
}
