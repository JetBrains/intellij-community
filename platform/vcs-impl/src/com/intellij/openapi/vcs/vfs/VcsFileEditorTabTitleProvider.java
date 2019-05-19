// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.vfs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsFileEditorTabTitleProvider extends UniqueNameEditorTabTitleProvider {
  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof AbstractVcsVirtualFile) {
      String baseName = super.getEditorTabTitle(project, file);
      if (baseName == null && UISettings.getInstance().getHideKnownExtensionInTabs() && !file.isDirectory()) {
        String nameWithoutExtension = file.getNameWithoutExtension();
        baseName = nameWithoutExtension.isEmpty() ? file.getName() : nameWithoutExtension;
      }

      if (baseName != null) {
        return ((AbstractVcsVirtualFile)file).getPresentableName(baseName);
      }
    }
    return null;
  }
}
