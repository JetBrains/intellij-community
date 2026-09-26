// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.structureView.impl.actions;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.structureView.impl.StructurePopup;
import com.intellij.platform.structureView.impl.StructurePopupProvider;
import com.intellij.platform.structureView.impl.StructurePopupTestExt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

@ApiStatus.Internal
public final class ViewStructureAction extends ViewStructureActionBase {

  @Override
  protected void showFileStructurePopup(@NotNull Project project,
                                        @NotNull FileEditor fileEditor) {
    StructurePopup popup = createPopup(project, fileEditor);
    if (popup == null) return;

    VirtualFile virtualFile = fileEditor.getFile();
    String title = virtualFile == null ? fileEditor.getName() : virtualFile.getName();
    popup.setTitle(title);
    popup.show();
  }

  public static @Nullable StructurePopup createPopup(@NotNull Project project, @NotNull FileEditor fileEditor) {
    return StructurePopupProvider.EP.getExtensionList().stream()
      .map(provider -> provider.createPopup(project, fileEditor))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  @ApiStatus.Internal
  @TestOnly
  public static @Nullable StructurePopupTestExt createPopupForTest(@NotNull Project project, @NotNull FileEditor fileEditor) {
    StructurePopup popup = createPopup(project, fileEditor);
    return popup instanceof StructurePopupTestExt ? (StructurePopupTestExt)popup : null;
  }
}