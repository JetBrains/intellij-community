// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

final class TestDataGroupEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  public @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    if (!(file instanceof TestDataGroupVirtualFile)) {
      return null;
    }

    // TestDataGroupVirtualFile.getName() implementation is fine
    return file.getName();
  }
}
