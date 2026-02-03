// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface StructureViewExtension {
  Class<? extends PsiElement> getType();

  StructureViewTreeElement[] getChildren(PsiElement parent);

  @Nullable
  Object getCurrentEditorElement(Editor editor, PsiElement parent);

  default void filterChildren(@NotNull Collection<StructureViewTreeElement> baseChildren, @NotNull List<StructureViewTreeElement> extensionChildren) {
  }
}
