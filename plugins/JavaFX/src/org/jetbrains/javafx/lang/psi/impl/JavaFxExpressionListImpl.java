package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxExpressionList;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:40:15
 */
public class JavaFxExpressionListImpl extends JavaFxBaseElementImpl implements JavaFxExpressionList {
  public JavaFxExpressionListImpl(@NotNull ASTNode node) {
    super(node);
  }
}
