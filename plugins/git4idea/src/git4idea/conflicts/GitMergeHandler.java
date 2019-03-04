// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.merge.GitDefaultMergeDialogCustomizer;
import git4idea.merge.GitMergeUtil;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

public class GitMergeHandler {
  private static final Logger LOG = Logger.getInstance(GitMergeHandler.class);

  @NotNull private final Project myProject;

  public GitMergeHandler(@NotNull Project project) {
    myProject = project;
  }

  public boolean canResolveConflict(@NotNull GitConflict conflict) {
    VirtualFile file = conflict.getFilePath().getVirtualFile();
    if (file == null) return false;
    return !file.isDirectory(); // Can't handle conflicts in submodules
  }

  @NotNull
  public Resolver resolveConflict(@NotNull GitConflict conflict) throws VcsException {
    VirtualFile root = conflict.getRoot();

    FilePath path = conflict.getFilePath();
    boolean isReverse = conflict.isReversed();

    VirtualFile file = path.getVirtualFile();
    if (file == null) throw new VcsException("Can't find file for " + path);

    MergeData mergeData = GitMergeUtil.loadMergeData(myProject, root, path, isReverse);
    GitDefaultMergeDialogCustomizer dialogCustomizer = new GitDefaultMergeDialogCustomizer(myProject);

    return new Resolver(conflict, file, mergeData, dialogCustomizer);
  }

  public void acceptOneVersion(@NotNull Collection<GitConflict> conflicts, boolean takeTheirs) throws VcsException {
    MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(conflicts);

    for (VirtualFile root : byRoot.keySet()) {
      Collection<GitConflict> rootConflicts = byRoot.get(root);

      List<GitConflict> acceptYours = ContainerUtil.filter(rootConflicts, it -> it.isReversed() == takeTheirs);
      List<GitConflict> acceptTheirs = ContainerUtil.filter(rootConflicts, it -> it.isReversed() != takeTheirs);

      if (!acceptYours.isEmpty()) {
        GitMergeUtil.acceptOneVersion(myProject, root, acceptYours, GitConflict.ConflictSide.OURS);
      }
      if (!acceptTheirs.isEmpty()) {
        GitMergeUtil.acceptOneVersion(myProject, root, acceptTheirs, GitConflict.ConflictSide.THEIRS);
      }
    }
  }

  @NotNull
  public static MultiMap<VirtualFile, GitConflict> groupConflictsByRoot(@NotNull Collection<GitConflict> conflicts) {
    MultiMap<VirtualFile, GitConflict> byRoot = MultiMap.create();
    for (GitConflict conflict : conflicts) {
      byRoot.putValue(conflict.getRoot(), conflict);
    }
    return byRoot;
  }

  public class Resolver {
    @NotNull private final GitConflict myConflict;
    @NotNull private final MergeData myMergeData;
    @NotNull private final VirtualFile myFile;
    @NotNull private final GitDefaultMergeDialogCustomizer myDialogCustomizer;

    private Resolver(@NotNull GitConflict conflict,
                     @NotNull VirtualFile file,
                     @NotNull MergeData mergeData,
                     @NotNull GitDefaultMergeDialogCustomizer dialogCustomizer) {
      myConflict = conflict;
      myMergeData = mergeData;
      myFile = file;
      myDialogCustomizer = dialogCustomizer;
    }

    @NotNull
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @NotNull
    public MergeData getMergeData() {
      return myMergeData;
    }

    public void conflictResolved() {
      FilePath path = myConflict.getFilePath();

      VirtualFile root = myConflict.getRoot();
      try {
        GitFileUtils.addPathsForce(myProject, root, singletonList(path));
      }
      catch (VcsException e) {
        LOG.error(String.format("Unexpected exception during the git operation: file - %s)", path), e);
      }
    }


    @NotNull
    public String getMergeWindowTitle() {
      return myDialogCustomizer.getMergeWindowTitle(myFile);
    }

    @NotNull
    public String getLeftPanelTitle() {
      return myDialogCustomizer.getLeftPanelTitle(myFile);
    }

    @NotNull
    public String getCenterPanelTitle() {
      return myDialogCustomizer.getCenterPanelTitle(myFile);
    }

    @NotNull
    public String getRightPanelTitle() {
      return myDialogCustomizer.getRightPanelTitle(myFile, myMergeData.LAST_REVISION_NUMBER);
    }
  }
}
