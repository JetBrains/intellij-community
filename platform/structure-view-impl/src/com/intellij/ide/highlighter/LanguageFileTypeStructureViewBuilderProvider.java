// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.ide.structureView.logical.PhysicalAndLogicalStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class LanguageFileTypeStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull FileType fileType, @NotNull VirtualFile file, @NotNull Project project) {
    if (!(fileType instanceof LanguageFileType)) return null;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;

    PsiStructureViewFactory factory = LanguageStructureViewBuilder.getInstance().forLanguage(psiFile.getLanguage());
    if (factory == null) return null;
    StructureViewBuilder physicalBuilder = factory.getStructureViewBuilder(psiFile);
    return PhysicalAndLogicalStructureViewBuilder.Companion.wrapPhysicalBuilderIfPossible(physicalBuilder, psiFile);
  }
}