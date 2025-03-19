// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Conflict resolver that makes a merge commit after all conflicts are resolved.
 */
public class GitMergeCommittingConflictResolver extends GitConflictResolver {
  private final @Unmodifiable Collection<? extends VirtualFile> myMergingRoots;
  private final boolean myRefreshAfterCommit;

  public GitMergeCommittingConflictResolver(@NotNull Project project,
                                            @NotNull Git git,
                                            @NotNull GitMerger merger,
                                            @NotNull Collection<? extends VirtualFile> mergingRoots,
                                            @NotNull Params params,
                                            boolean refreshAfterCommit) {
    this(project, mergingRoots, params, refreshAfterCommit);
  }

  public GitMergeCommittingConflictResolver(@NotNull Project project,
                                            @NotNull @Unmodifiable Collection<? extends VirtualFile> mergingRoots,
                                            @NotNull Params params,
                                            boolean refreshAfterCommit) {
    super(project, mergingRoots, params);
    myMergingRoots = mergingRoots;
    myRefreshAfterCommit = refreshAfterCommit;
  }

  @Override
  protected boolean proceedAfterAllMerged() throws VcsException {
    new GitMerger(myProject).mergeCommit(myMergingRoots);
    if (myRefreshAfterCommit) {
      for (VirtualFile root : myMergingRoots) {
        root.refresh(true, true);
      }
    }
    return true;
  }
}
