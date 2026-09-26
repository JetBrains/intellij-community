// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.symbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Root element for the symbol-based language structure view. Makes transition from psi to symbol. Psi file may have no symbol by itself, so
 * root element is psi based and children are symbol based.
 */
public class FileSymbolTreeElement extends PsiTreeElementBase<PsiFile> {
  private static final Logger LOG = Logger.getInstance(FileSymbolTreeElement.class);

  public FileSymbolTreeElement(@NotNull PsiFile psiFile) {
    super(psiFile);
  }

  @Override
  public @Nullable String getPresentableText() {
    var element = getElement();
    if (element == null) {
      LOG.error("Attempt to render invalid file presentation");
      return null;
    }
    return element.getName();
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }
}
