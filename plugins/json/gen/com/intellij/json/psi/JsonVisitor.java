// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class JsonVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull JsonArray o) {
    visitValue(o);
  }

  public void visitBooleanLiteral(@NotNull JsonBooleanLiteral o) {
    visitLiteral(o);
  }

  public void visitLiteral(@NotNull JsonLiteral o) {
    visitValue(o);
  }

  public void visitNullLiteral(@NotNull JsonNullLiteral o) {
    visitLiteral(o);
  }

  public void visitNumberLiteral(@NotNull JsonNumberLiteral o) {
    visitLiteral(o);
  }

  public void visitObject(@NotNull JsonObject o) {
    visitValue(o);
  }

  public void visitProperty(@NotNull JsonProperty o) {
    visitPsiElement(o);
  }

  public void visitPropertyName(@NotNull JsonPropertyName o) {
    visitPsiElement(o);
  }

  public void visitStringLiteral(@NotNull JsonStringLiteral o) {
    visitLiteral(o);
  }

  public void visitValue(@NotNull JsonValue o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
