// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class CharsetEP implements PatchEP {
  private final static Key<Map<String, String>> ourName = Key.create("Charset");

  @Override
  public @NotNull String getName() {
    return "com.intellij.openapi.diff.impl.patch.CharsetEP";
  }

  @Override
  public CharSequence provideContent(@NotNull Project project, @NotNull String path, @Nullable CommitContext commitContext) {
    Path file = ProjectKt.getStateStore(project).getProjectBasePath().resolve(path);
    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    return vf == null ? null : vf.getCharset().name();
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull Project project,
                                               @NotNull String path,
                                               @NotNull CharSequence content,
                                               @Nullable CommitContext commitContext) {
    if (commitContext == null) {
      return;
    }

    Map<String, String> map = commitContext.getUserData(ourName);
    if (map == null) {
      map = new HashMap<>();
      commitContext.putUserData(ourName, map);
    }
    Path file = ProjectKt.getStateStore(project).getProjectBasePath().resolve(path);
    map.put(FilePathsHelper.convertPath(file.toString()), content.toString());
  }

  /**
   * @param path absolute path to a file
   */
  public static @Nullable String getCharset(@NotNull String path, @NotNull CommitContext commitContext) {
    Map<String, String> userData = commitContext.getUserData(ourName);
    return userData == null ? null : userData.get(FilePathsHelper.convertPath(path));
  }
}
