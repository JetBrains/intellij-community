package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxModifierList;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxModifierListImpl extends JavaFxBaseElementImpl implements JavaFxModifierList {
  public JavaFxModifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitModifierList(this);
  }
}
