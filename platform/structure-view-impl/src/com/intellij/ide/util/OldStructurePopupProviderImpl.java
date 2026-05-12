// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.idea.AppMode;
import com.intellij.platform.structureView.impl.StructurePopupProvider;
import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


class OldStructurePopupProviderImpl implements StructurePopupProvider {
  @Override
  public @Nullable FileStructurePopup createPopup(@NotNull Project project,
                                                  @NotNull FileEditor fileEditor) {
    if (!AppMode.isRemoteDevHost() && FileStructureUtil.isSplitPopupEnabled()) return null;
    return ViewStructureAction.createPopup(project, fileEditor);
  }
}
