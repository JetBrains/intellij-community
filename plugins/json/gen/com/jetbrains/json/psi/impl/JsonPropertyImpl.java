// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonPropertyImpl extends ASTWrapperPsiElement implements JsonProperty {

  public JsonPropertyImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitProperty(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JsonPropertyName getPropertyName() {
    return findNotNullChildByClass(JsonPropertyName.class);
  }

  @Override
  @Nullable
  public JsonValue getValue() {
    return findChildByClass(JsonValue.class);
  }

  @NotNull
  public String getName() {
    return JsonPsiImplUtils.getName(this);
  }

}
