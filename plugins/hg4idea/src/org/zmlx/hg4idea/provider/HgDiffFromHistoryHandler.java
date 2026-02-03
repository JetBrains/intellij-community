// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.BaseDiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;

/**
 * {@link DiffFromHistoryHandler#showDiffForTwo(Project, FilePath, VcsFileRevision, VcsFileRevision) "Show Diff" for 2 revision} calls the common code.
 */
public class HgDiffFromHistoryHandler extends BaseDiffFromHistoryHandler<HgFileRevision> {

  private static final Logger LOG = Logger.getInstance(HgDiffFromHistoryHandler.class);

  public HgDiffFromHistoryHandler(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull List<Change> getChangesBetweenRevisions(@NotNull FilePath path, @NotNull HgFileRevision rev1, @Nullable HgFileRevision rev2) {
    return executeDiff(path, rev1, rev2);
  }

  @Override
  protected @NotNull List<Change> getAffectedChanges(@NotNull FilePath path, @NotNull HgFileRevision rev) {
    return executeDiff(path, null, rev);
  }

  @Override
  protected @NotNull String getPresentableName(@NotNull HgFileRevision revision) {
    return revision.getRevisionNumber().getChangeset();
  }

  private @NotNull List<Change> executeDiff(@NotNull FilePath path, @Nullable HgFileRevision rev1, @Nullable HgFileRevision rev2) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    LOG.assertTrue(root != null, "Repository is null for " + path);

    return HgUtil
      .getDiff(myProject, root, path, rev1 != null ? rev1.getRevisionNumber() : null, rev2 != null ? rev2.getRevisionNumber() : null);
  }
}