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
import git4idea.repo.GitConflict;
import git4idea.repo.GitConflict.ConflictSide;
import git4idea.repo.GitConflict.Status;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

public class GitMergeHandler {
  private static final Logger LOG = Logger.getInstance(GitMergeHandler.class);

  @NotNull private final Project myProject;
  @NotNull private final GitDefaultMergeDialogCustomizer myDialogCustomizer;

  public GitMergeHandler(@NotNull Project project) {
    myProject = project;
    myDialogCustomizer = new GitDefaultMergeDialogCustomizer(project);
  }

  public boolean canResolveConflict(@NotNull GitConflict conflict) {
    VirtualFile file = conflict.getFilePath().getVirtualFile();
    if (file == null) return false;
    if (file.isDirectory()) return false; // Can't handle conflicts in submodules
    return conflict.getStatus(ConflictSide.OURS) != Status.DELETED ||
           conflict.getStatus(ConflictSide.THEIRS) != Status.DELETED;
  }

  @NotNull
  public Resolver resolveConflict(@NotNull GitConflict conflict, boolean isReversed) throws VcsException {
    VirtualFile root = conflict.getRoot();
    FilePath path = conflict.getFilePath();

    VirtualFile file = path.getVirtualFile();
    if (file == null) throw new VcsException("Can't find file for " + path);

    MergeData mergeData = GitMergeUtil.loadMergeData(myProject, root, path, isReversed);

    String windowTitle = myDialogCustomizer.getMergeWindowTitle(file);
    String leftTitle = myDialogCustomizer.getLeftPanelTitle(file);
    String centerTitle = myDialogCustomizer.getCenterPanelTitle(file);
    String rightTitle = myDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER);

    return new Resolver(conflict, file, mergeData,
                        windowTitle, ContainerUtil.list(leftTitle, centerTitle, rightTitle));
  }

  public void acceptOneVersion(@NotNull Collection<GitConflict> conflicts,
                               @NotNull Collection<VirtualFile> reversedRoots,
                               boolean takeTheirs) throws VcsException {
    MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(conflicts);

    for (VirtualFile root : byRoot.keySet()) {
      Collection<GitConflict> rootConflicts = byRoot.get(root);
      boolean isReversed = reversedRoots.contains(root);
      ConflictSide side = isReversed == takeTheirs ? ConflictSide.OURS
                                                   : ConflictSide.THEIRS;

      GitMergeUtil.acceptOneVersion(myProject, root, rootConflicts, side);
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

    @NotNull private final String myWindowTitle;
    @NotNull private final List<String> myContentTitles;

    private Resolver(@NotNull GitConflict conflict,
                     @NotNull VirtualFile file,
                     @NotNull MergeData mergeData,
                     @NotNull String windowTitle,
                     @NotNull List<String> contentTitles) {
      myConflict = conflict;
      myMergeData = mergeData;
      myFile = file;
      myWindowTitle = windowTitle;
      myContentTitles = contentTitles;
    }

    @NotNull
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @NotNull
    public MergeData getMergeData() {
      return myMergeData;
    }

    public void onConflictResolved() {
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
    public String getWindowTitle() {
      return myWindowTitle;
    }

    @NotNull
    public List<String> getContentTitles() {
      return myContentTitles;
    }
  }
}
