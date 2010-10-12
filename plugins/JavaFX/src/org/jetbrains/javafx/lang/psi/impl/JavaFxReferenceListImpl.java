package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceList;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxReferenceListImpl extends JavaFxBaseElementImpl implements JavaFxReferenceList {
  public JavaFxReferenceListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxReferenceElement[] getReferenceElements() {
    return (JavaFxReferenceElement[])childrenToPsi(TokenSet.create(JavaFxElementTypes.REFERENCE_ELEMENT), JavaFxReferenceElement.EMPTY_ARRAY);
  }
}
