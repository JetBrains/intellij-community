// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class IgnoreVisitor extends PsiElementVisitor {

  public void visitEntry(@NotNull IgnoreEntry o) {
    visitEntryBase(o);
  }

  public void visitEntryDirectory(@NotNull IgnoreEntryDirectory o) {
    visitEntryFile(o);
  }

  public void visitEntryFile(@NotNull IgnoreEntryFile o) {
    visitEntry(o);
  }

  public void visitNegation(@NotNull IgnoreNegation o) {
    visitPsiElement(o);
  }

  public void visitSyntax(@NotNull IgnoreSyntax o) {
    visitPsiElement(o);
  }

  public void visitEntryBase(@NotNull IgnoreEntryBase o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
