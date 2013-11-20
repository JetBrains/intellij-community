// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.json.psi.JsonArray;
import com.jetbrains.json.psi.JsonValue;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonArrayImpl extends JsonValueImpl implements JsonArray {

  public JsonArrayImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitArray(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<JsonValue> getValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

}
