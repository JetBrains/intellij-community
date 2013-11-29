// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.JsonLiteral;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

public class JsonLiteralImpl extends JsonValueImpl implements JsonLiteral {

  public JsonLiteralImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitLiteral(this);
    else super.accept(visitor);
  }

}
