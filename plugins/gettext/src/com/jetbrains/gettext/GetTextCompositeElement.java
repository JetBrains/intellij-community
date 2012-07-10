package com.jetbrains.gettext;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextCompositeElement extends ASTWrapperPsiElement {
  public GetTextCompositeElement(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public Language getLanguage() {
    return GetTextLanguage.INSTANCE;
  }

  @NotNull
  @SuppressWarnings({ "ConstantConditions", "EmptyMethod" })
  public ASTNode getNode() {
    return super.getNode();
  }

  public String toString() {
    return getNode().getElementType().toString();
  }
}