// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CharsetEP implements PatchEP {
  private final static Key<Map<String, String>> ourName = Key.create("Charset");

  private final String myBaseDir;

  public CharsetEP(Project project) {
    myBaseDir = project.getBasePath();
  }

  @NotNull
  @Override
  public String getName() {
    return "com.intellij.openapi.diff.impl.patch.CharsetEP";
  }

  @Override
  public CharSequence provideContent(@NotNull String path, CommitContext commitContext) {
    final File file = new File(myBaseDir, path);
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (vf == null) return null;
    CharSequence charsetName = vf.getCharset().name();
    return charsetName;
  }

  @Override
  public void consumeContent(@NotNull String path, @NotNull CharSequence content, CommitContext commitContext) {
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull String path,
                                               @NotNull CharSequence content,
                                               CommitContext commitContext) {
    if (commitContext == null) return;
    Map<String, String> map = commitContext.getUserData(ourName);
    if (map == null) {
      map = new HashMap<>();
      commitContext.putUserData(ourName, map);
    }
    final File file = new File(myBaseDir, path);
    map.put(FilePathsHelper.convertPath(file.getPath()), content.toString());
  }
  
  public static String getCharset(final String path, final CommitContext commitContext) {
    if (commitContext == null) return null;
    final Map<String, String> userData = commitContext.getUserData(ourName);
    return userData == null ? null : userData.get(FilePathsHelper.convertPath(path));
  }
}
