// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.json.psi.JsonArray;
import com.jetbrains.json.psi.JsonPropertyValue;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonArrayImpl extends JsonPropertyValueImpl implements JsonArray {

  public JsonArrayImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitArray(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<JsonPropertyValue> getPropertyValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPropertyValue.class);
  }

}
