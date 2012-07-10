package com.jetbrains.gettext;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextCompositeElementType extends IElementType implements ICompositeElementType {
  public GetTextCompositeElementType(@NotNull @NonNls final String debugName) {
    super(debugName, GetTextLanguage.INSTANCE);
  }

  public static PsiElement createPsiElement(ASTNode node) {
    return new GetTextCompositeElement(node);
  }

  @NotNull
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }
}