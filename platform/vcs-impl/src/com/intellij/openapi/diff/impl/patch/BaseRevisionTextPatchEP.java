// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class BaseRevisionTextPatchEP implements PatchEP {
  public final static Key<Boolean> ourPutBaseRevisionTextKey = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourPutBaseRevisionTextKey");
  public static final Key<Map<FilePath, ContentRevision>>
    ourBaseRevisions = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourBaseRevisionPaths");
  public static final Key<Map<String, String>> ourStoredTexts = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourStoredTexts");
  private final static Logger LOG = Logger.getInstance(BaseRevisionTextPatchEP.class);

  @NotNull
  @Override
  public String getName() {
    return "com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP";
  }

  @Override
  public CharSequence provideContent(@NotNull Project project, @NotNull String path, @Nullable CommitContext commitContext) {
    if (commitContext == null) {
      return null;
    }

    if (Boolean.TRUE.equals(commitContext.getUserData(ourPutBaseRevisionTextKey))) {
      Path file = ProjectKt.getStateStore(project).getProjectBasePath().resolve(path);
      FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePath(file, Files.isDirectory(file));
      Map<FilePath, ContentRevision> baseRevisions = commitContext.getUserData(ourBaseRevisions);
      ContentRevision baseRevision = baseRevisions == null ? null : baseRevisions.get(filePath);
      if (baseRevision == null) {
        return null;
      }

      try {
        return baseRevision.getContent();
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
    else {
      Map<String, String> map = commitContext.getUserData(ourStoredTexts);
      if (map != null) {
        return map.get(ProjectKt.getStateStore(project).getProjectBasePath().resolve(path).toString());
      }
    }
    return null;
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull Project project,
                                               @NotNull String path,
                                               @NotNull CharSequence content,
                                               @Nullable CommitContext commitContext) {
    if (commitContext == null) {
      return;
    }

    Map<String, String> map = commitContext.getUserData(ourStoredTexts);
    if (map == null) {
      map = new HashMap<>();
      commitContext.putUserData(ourStoredTexts, map);
    }
    map.put(ProjectKt.getStateStore(project).getProjectBasePath().resolve(path).toString(), content.toString());
  }
}
