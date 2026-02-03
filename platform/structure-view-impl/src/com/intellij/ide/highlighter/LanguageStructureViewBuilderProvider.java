// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.ide.structureView.logical.PhysicalAndLogicalStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class LanguageStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull FileType fileType, @NotNull VirtualFile file, @NotNull Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;

    StructureViewBuilder physicalBuilder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile);
    return PhysicalAndLogicalStructureViewBuilder.Companion.wrapPhysicalBuilderIfPossible(physicalBuilder, psiFile);
  }
}