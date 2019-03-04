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
      String name = super.getEditorTabTitle(project, file);
      String suffix = ((AbstractVcsVirtualFile)file).myRevision == null ? "" : " (" + ((AbstractVcsVirtualFile)file).myRevision + ")";
      if (name != null) {
        return name + suffix;
      } else {
        if (UISettings.getInstance().getHideKnownExtensionInTabs() && !file.isDirectory()) {
          final String nameWithoutExtension = file.getNameWithoutExtension();
          name = nameWithoutExtension.isEmpty() ? file.getName() : nameWithoutExtension;
          return name + suffix;
        }
      }
    }
    return null;
  }
}
