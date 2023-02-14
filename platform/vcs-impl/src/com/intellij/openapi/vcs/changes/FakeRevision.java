// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Change content stored in {@link ChangeListManagerSerialization} to keep change-to-changelist mappings between IDE restarts.
 * These are going to be replaced by real content revision after the next CLM refresh.
 */
public class FakeRevision implements ContentRevision {
  private final Project myProject;
  private final FilePath myFile;

  public FakeRevision(@NotNull Project project, @NotNull FilePath file) {
    myProject = project;
    myFile = file;
  }

  @Override
  @Nullable
  public String getContent() throws VcsException {
    VirtualFile virtualFile = myFile.getVirtualFile();
    if (virtualFile == null) return null;

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
    if (diffProvider == null) return null;

    ContentRevision delegateContent = diffProvider.createCurrentFileContent(virtualFile);
    if (delegateContent == null) return null;

    return delegateContent.getContent();
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
