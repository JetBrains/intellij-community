// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
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
public class FakeRevision implements ByteBackedContentRevision {
  private static final Logger LOG = Logger.getInstance(FakeRevision.class);

  private final Project myProject;
  private final FilePath myFile;
  private final boolean myCurrentRevision;

  /**
   * @deprecated Consider this class platform-only, use own ContentRevision implementation when needed.
   */
  @Deprecated
  public FakeRevision(@NotNull Project project, @NotNull FilePath file) {
    this(project, file, false);
  }

  public FakeRevision(@NotNull Project project, @NotNull FilePath file, boolean isCurrentRevision) {
    myProject = project;
    myFile = file;
    myCurrentRevision = isCurrentRevision;
  }

  @Override
  public byte @Nullable [] getContentAsBytes() throws VcsException {
    ContentRevision revision = createDelegateContentRevision();
    if (revision == null) return null;
    return ChangesUtil.loadContentRevision(revision);
  }

  @Override
  public @Nullable String getContent() throws VcsException {
    ContentRevision revision = createDelegateContentRevision();
    if (revision == null) return null;
    return revision.getContent();
  }

  private @Nullable ContentRevision createDelegateContentRevision() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("FakeRevision queried for " + myFile.getPath() + (myCurrentRevision ? " (current)" : ""), new Throwable());
    }

    if (myCurrentRevision) {
      return new CurrentContentRevision(myFile);
    }

    VirtualFile virtualFile = myFile.getVirtualFile();
    if (virtualFile == null) return null;

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
    if (diffProvider == null) return null;

    ContentRevision delegateContent = diffProvider.createCurrentFileContent(virtualFile);
    if (delegateContent == null) return null;

    return delegateContent;
  }

  @Override
  public @NotNull FilePath getFile() {
    return myFile;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
