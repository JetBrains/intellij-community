// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.finder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConfigFinder {

  private static final int MAX_EDITOR_CONFIG_LOOKUP_DEPTH = 5;

  private EditorConfigFinder() {
  }

  public static void startSearch(@NotNull VirtualFile root, @NotNull Callback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      searchParentEditorConfigs(root, callback);
      VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>(VirtualFileVisitor.limit(MAX_EDITOR_CONFIG_LOOKUP_DEPTH)) {
        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          if (isEditorConfig(file) && callback.found(file) == Callback.Result.Stop) {
            return SKIP_CHILDREN;
          }
          return CONTINUE;
        }
      });
      callback.done();
    });
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

  @Nullable
  private static VirtualFile getEditorConfigUnder(@NotNull VirtualFile dir) {
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
