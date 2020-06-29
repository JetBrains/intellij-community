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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class BaseRevisionTextPatchEP implements PatchEP {
  public final static Key<Boolean> ourPutBaseRevisionTextKey = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourPutBaseRevisionTextKey");
  public static final Key<Map<FilePath, ContentRevision>>
    ourBaseRevisions = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourBaseRevisionPaths");
  public static final Key<Map<String, String>> ourStoredTexts = Key.create("com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP.ourStoredTexts");
  private final static Logger LOG = Logger.getInstance(BaseRevisionTextPatchEP.class);

  private final String myBaseDir;

  public BaseRevisionTextPatchEP(final Project project) {
    myBaseDir = project.getBasePath();
  }

  @NotNull
  @Override
  public String getName() {
    return "com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP";
  }

  @Override
  public CharSequence provideContent(@NotNull String path, CommitContext commitContext) {
    if (commitContext == null) return null;
    if (Boolean.TRUE.equals(commitContext.getUserData(ourPutBaseRevisionTextKey))) {
      File file = new File(myBaseDir, path);
      FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
      Map<FilePath, ContentRevision> baseRevisions = commitContext.getUserData(ourBaseRevisions);
      if (baseRevisions == null) return null;
      ContentRevision baseRevision = baseRevisions.get(filePath);
      if (baseRevision == null) return null;
      try {
        return baseRevision.getContent();
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    } else {
      final Map<String, String> map = commitContext.getUserData(ourStoredTexts);
      if (map != null) {
        final File file = new File(myBaseDir, path);
        return map.get(file.getPath());
      }
    }
    return null;
  }

  @Override
  public void consumeContent(@NotNull String path, @NotNull CharSequence content, CommitContext commitContext) {
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull String path,
                                               @NotNull CharSequence content,
                                               CommitContext commitContext) {
    if (commitContext == null) return;
    Map<String, String> map = commitContext.getUserData(ourStoredTexts);
    if (map == null) {
      map = new HashMap<>();
      commitContext.putUserData(ourStoredTexts, map);
    }
    final File file = new File(myBaseDir, path);
    map.put(file.getPath(), content.toString());
  }
}
