// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.json.JsonParserTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.jetbrains.json.psi.*;

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
  public JsonPropertyValue getPropertyValue() {
    return findChildByClass(JsonPropertyValue.class);
  }

  @NotNull
  public String getName() {
    return JsonPsiImplUtils.getName(this);
  }

  public void delete() {
    JsonPsiImplUtils.delete(this);
  }

}
