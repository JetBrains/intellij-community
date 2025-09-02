// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.finder;

import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditorConfigFinder {

  private EditorConfigFinder() {
  }

  public static void searchParentEditorConfigs(@NotNull VirtualFile root,
                                               @NotNull Callback callback) {
    for (VirtualFile parentDir = root.getParent(); parentDir != null; parentDir = parentDir.getParent()) {
      VirtualFile editorConfig = getEditorConfigUnder(parentDir);
      if (editorConfig != null) {
        if (callback.found(editorConfig) == Callback.Result.Stop) {
          return;
        }
      }
    }
  }

  private static @Nullable VirtualFile getEditorConfigUnder(@NotNull VirtualFile dir) {
    for (VirtualFile file : dir.getChildren()) {
      if (isEditorConfig(file)) {
        return file;
      }
    }
    return null;
  }

  private static boolean isEditorConfig(@NotNull VirtualFile file) {
    return Utils.EDITOR_CONFIG_FILE_NAME.equals(file.getName());
  }

  public interface Callback {
    enum Result {Stop, Continue}

    Result found(@NotNull VirtualFile editorConfigFile);
    void done();
  }
}
