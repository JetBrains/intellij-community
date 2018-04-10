/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
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

  @NotNull
  @Override
  protected List<Change> getChangesBetweenRevisions(@NotNull FilePath path, @NotNull HgFileRevision rev1, @Nullable HgFileRevision rev2) {
    return executeDiff(path, rev1, rev2);
  }

  @NotNull
  @Override
  protected List<Change> getAffectedChanges(@NotNull FilePath path, @NotNull HgFileRevision rev) {
    return executeDiff(path, null, rev);
  }

  @NotNull
  @Override
  protected String getPresentableName(@NotNull HgFileRevision revision) {
    return revision.getRevisionNumber().getChangeset();
  }

  @NotNull
  private List<Change> executeDiff(@NotNull FilePath path, @Nullable HgFileRevision rev1, @Nullable HgFileRevision rev2) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    LOG.assertTrue(root != null, "Repository is null for " + path);

    return HgUtil
      .getDiff(myProject, root, path, rev1 != null ? rev1.getRevisionNumber() : null, rev2 != null ? rev2.getRevisionNumber() : null);
  }
}