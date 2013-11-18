// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.JsonPropertyValue;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

public class JsonPropertyValueImpl extends ASTWrapperPsiElement implements JsonPropertyValue {

  public JsonPropertyValueImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitPropertyValue(this);
    else super.accept(visitor);
  }

}
