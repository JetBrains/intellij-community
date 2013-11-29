// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.json.psi.JsonObject;
import com.jetbrains.json.psi.JsonProperty;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonObjectImpl extends JsonValueImpl implements JsonObject {

  public JsonObjectImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitObject(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<JsonProperty> getPropertyList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonProperty.class);
  }

}
