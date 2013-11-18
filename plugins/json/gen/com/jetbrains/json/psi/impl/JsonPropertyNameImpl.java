// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.JsonPropertyName;
import com.jetbrains.json.psi.JsonStringLiteral;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

public class JsonPropertyNameImpl extends ASTWrapperPsiElement implements JsonPropertyName {

  public JsonPropertyNameImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitPropertyName(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JsonStringLiteral getStringLiteral() {
    return findNotNullChildByClass(JsonStringLiteral.class);
  }

}
