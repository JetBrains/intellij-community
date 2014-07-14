// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.JsonNumberLiteral;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.json.JsonElementTypes.NUMBER;

public class JsonNumberLiteralImpl extends JsonLiteralImpl implements JsonNumberLiteral {

  public JsonNumberLiteralImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitNumberLiteral(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getNumber() {
    return findNotNullChildByType(NUMBER);
  }

}
