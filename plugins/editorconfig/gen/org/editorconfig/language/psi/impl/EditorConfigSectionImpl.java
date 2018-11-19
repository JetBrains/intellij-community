// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.editorconfig.language.psi.EditorConfigHeader;
import org.editorconfig.language.psi.EditorConfigOption;
import org.editorconfig.language.psi.EditorConfigSection;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.editorconfig.language.psi.base.EditorConfigSectionBase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorConfigSectionImpl extends EditorConfigSectionBase implements EditorConfigSection {

  public EditorConfigSectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitSection(this);
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
  @NotNull
  public EditorConfigHeader getHeader() {
    return findNotNullChildByClass(EditorConfigHeader.class);
  }

  @Override
  @NotNull
  public List<EditorConfigOption> getOptionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigOption.class);
  }

}
