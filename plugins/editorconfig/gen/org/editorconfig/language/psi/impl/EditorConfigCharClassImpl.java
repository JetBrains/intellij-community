// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.editorconfig.language.psi.EditorConfigCharClass;
import org.editorconfig.language.psi.EditorConfigCharClassExclamation;
import org.editorconfig.language.psi.EditorConfigCharClassLetter;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.editorconfig.language.psi.base.EditorConfigHeaderElementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorConfigCharClassImpl extends EditorConfigHeaderElementBase implements EditorConfigCharClass {

  public EditorConfigCharClassImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitCharClass(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) {
      accept((EditorConfigVisitor)visitor);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  @Nullable
  public EditorConfigCharClassExclamation getCharClassExclamation() {
    return findChildByClass(EditorConfigCharClassExclamation.class);
  }

  @Override
  @NotNull
  public List<EditorConfigCharClassLetter> getCharClassLetterList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigCharClassLetter.class);
  }
}
