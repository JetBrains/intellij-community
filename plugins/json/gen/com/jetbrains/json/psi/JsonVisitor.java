// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class JsonVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull JsonArray o) {
    visitPropertyValue(o);
  }

  public void visitLiteral(@NotNull JsonLiteral o) {
    visitPropertyValue(o);
  }

  public void visitObject(@NotNull JsonObject o) {
    visitPropertyValue(o);
  }

  public void visitProperty(@NotNull JsonProperty o) {
    visitPsiElement(o);
  }

  public void visitPropertyName(@NotNull JsonPropertyName o) {
    visitPsiElement(o);
  }

  public void visitPropertyValue(@NotNull JsonPropertyValue o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
