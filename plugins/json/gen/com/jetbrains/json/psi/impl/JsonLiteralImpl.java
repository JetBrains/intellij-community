// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.json.JsonParserTypes.*;
import com.jetbrains.json.psi.*;

public class JsonLiteralImpl extends JsonLiteralMixin implements JsonLiteral {

  public JsonLiteralImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitLiteral(this);
    else super.accept(visitor);
  }

  public boolean isQuotedString() {
    return JsonPsiImplUtils.isQuotedString(this);
  }

}
