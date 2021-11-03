// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement;
import org.editorconfig.language.psi.interfaces.EditorConfigIdentifierElement;
import com.intellij.psi.NavigatablePsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;

public class EditorConfigVisitor extends PsiElementVisitor {

  public void visitAsteriskPattern(@NotNull EditorConfigAsteriskPattern o) {
    visitHeaderElement(o);
  }

  public void visitCharClass(@NotNull EditorConfigCharClass o) {
    visitHeaderElement(o);
  }

  public void visitCharClassExclamation(@NotNull EditorConfigCharClassExclamation o) {
    visitHeaderElement(o);
  }

  public void visitCharClassLetter(@NotNull EditorConfigCharClassLetter o) {
    visitHeaderElement(o);
  }

  public void visitDoubleAsteriskPattern(@NotNull EditorConfigDoubleAsteriskPattern o) {
    visitHeaderElement(o);
  }

  public void visitFlatOptionKey(@NotNull EditorConfigFlatOptionKey o) {
    visitIdentifierElement(o);
  }

  public void visitFlatPattern(@NotNull EditorConfigFlatPattern o) {
    visitHeaderElement(o);
  }

  public void visitHeader(@NotNull EditorConfigHeader o) {
    visitHeaderElement(o);
  }

  public void visitOption(@NotNull EditorConfigOption o) {
    visitDescribableElement(o);
  }

  public void visitOptionValueIdentifier(@NotNull EditorConfigOptionValueIdentifier o) {
    visitIdentifierElement(o);
  }

  public void visitOptionValueList(@NotNull EditorConfigOptionValueList o) {
    visitDescribableElement(o);
  }

  public void visitOptionValuePair(@NotNull EditorConfigOptionValuePair o) {
    visitDescribableElement(o);
  }

  public void visitPattern(@NotNull EditorConfigPattern o) {
    visitHeaderElement(o);
  }

  public void visitPatternEnumeration(@NotNull EditorConfigPatternEnumeration o) {
    visitHeaderElement(o);
  }

  public void visitQualifiedKeyPart(@NotNull EditorConfigQualifiedKeyPart o) {
    visitIdentifierElement(o);
  }

  public void visitQualifiedOptionKey(@NotNull EditorConfigQualifiedOptionKey o) {
    visitDescribableElement(o);
  }

  public void visitQuestionPattern(@NotNull EditorConfigQuestionPattern o) {
    visitHeaderElement(o);
  }

  public void visitRawOptionValue(@NotNull EditorConfigRawOptionValue o) {
    visitPsiElement(o);
  }

  public void visitRootDeclaration(@NotNull EditorConfigRootDeclaration o) {
    visitNavigatablePsiElement(o);
  }

  public void visitRootDeclarationKey(@NotNull EditorConfigRootDeclarationKey o) {
    visitPsiElement(o);
  }

  public void visitRootDeclarationValue(@NotNull EditorConfigRootDeclarationValue o) {
    visitPsiElement(o);
  }

  public void visitSection(@NotNull EditorConfigSection o) {
    visitNavigatablePsiElement(o);
  }

  public void visitNavigatablePsiElement(@NotNull NavigatablePsiElement o) {
    visitElement(o);
  }

  public void visitDescribableElement(@NotNull EditorConfigDescribableElement o) {
    visitPsiElement(o);
  }

  public void visitHeaderElement(@NotNull EditorConfigHeaderElement o) {
    visitPsiElement(o);
  }

  public void visitIdentifierElement(@NotNull EditorConfigIdentifierElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
