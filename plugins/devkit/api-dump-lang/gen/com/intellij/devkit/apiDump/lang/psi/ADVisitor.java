// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;

public class ADVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull ADArray o) {
    visitPsiElement(o);
  }

  public void visitClassDeclaration(@NotNull ADClassDeclaration o) {
    visitPsiElement(o);
  }

  public void visitClassHeader(@NotNull ADClassHeader o) {
    visitPsiElement(o);
  }

  public void visitCompanion(@NotNull ADCompanion o) {
    visitMember(o);
  }

  public void visitConstructor(@NotNull ADConstructor o) {
    visitMember(o);
  }

  public void visitConstructorReference(@NotNull ADConstructorReference o) {
    visitPsiElement(o);
  }

  public void visitExperimental(@NotNull ADExperimental o) {
    visitPsiElement(o);
  }

  public void visitField(@NotNull ADField o) {
    visitMember(o);
  }

  public void visitFieldReference(@NotNull ADFieldReference o) {
    visitPsiElement(o);
  }

  public void visitMember(@NotNull ADMember o) {
    visitPsiElement(o);
  }

  public void visitMethod(@NotNull ADMethod o) {
    visitMember(o);
  }

  public void visitMethodReference(@NotNull ADMethodReference o) {
    visitPsiElement(o);
  }

  public void visitModifier(@NotNull ADModifier o) {
    visitPsiElement(o);
  }

  public void visitModifiers(@NotNull ADModifiers o) {
    visitPsiElement(o);
  }

  public void visitParameter(@NotNull ADParameter o) {
    visitPsiElement(o);
  }

  public void visitParameters(@NotNull ADParameters o) {
    visitPsiElement(o);
  }

  public void visitSuperType(@NotNull ADSuperType o) {
    visitMember(o);
  }

  public void visitTypeReference(@NotNull ADTypeReference o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull ADPsiElement o) {
    visitElement(o);
  }

}
