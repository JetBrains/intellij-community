// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.conflicts.GitConflict;
import git4idea.conflicts.GitConflict.ConflictSide;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Merge-changes provider for Git, used by IDEA internal 3-way merge tool
 */
public class GitMergeProvider implements MergeProvider2 {
  private static final Logger LOG = Logger.getInstance(GitMergeProvider.class);

  @NotNull private final Project myProject;
  /**
   * If true the merge provider has a reverse meaning, i. e. yours and theirs are swapped.
   * It should be used when conflict is resolved after rebase or unstash.
   */
  @NotNull private final Set<VirtualFile> myReverseRoots;

  private enum ReverseRequest {
    REVERSE,
    FORWARD,
    DETECT
  }

  private GitMergeProvider(@NotNull Project project, @NotNull Set<VirtualFile> reverseRoots) {
    myProject = project;
    myReverseRoots = reverseRoots;
  }

  public GitMergeProvider(@NotNull Project project, boolean reverse) {
    this(project, findReverseRoots(project, reverse ? ReverseRequest.REVERSE : ReverseRequest.FORWARD));
  }

  @NotNull
  public static MergeProvider detect(@NotNull Project project) {
    return new GitMergeProvider(project, findReverseRoots(project, ReverseRequest.DETECT));
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  private static Set<VirtualFile> findReverseRoots(@NotNull Project project, @NotNull ReverseRequest reverseOrDetect) {
    Set<VirtualFile> reverseMap = ContainerUtil.newHashSet();
    for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
      boolean reverse;
      if (reverseOrDetect == ReverseRequest.DETECT) {
        reverse = GitMergeUtil.isReverseRoot(repository);
      }
      else {
        reverse = reverseOrDetect == ReverseRequest.REVERSE;
      }
      if (reverse) {
        reverseMap.add(repository.getRoot());
      }
    }
    return reverseMap;
  }

  @Override
  @NotNull
  public MergeData loadRevisions(@NotNull final VirtualFile file) throws VcsException {
    final Ref<MergeData> mergeDataRef = new Ref<>(new MergeData());
    final VirtualFile root = GitUtil.getRepositoryForFile(myProject, file).getRoot();
    final FilePath path = VcsUtil.getFilePath(file);

    VcsRunnable runnable = new VcsRunnable() {
      @Override
      public void run() throws VcsException {
        mergeDataRef.set(GitMergeUtil.loadMergeData(myProject, root, path, myReverseRoots.contains(root)));
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, GitBundle.message("merge.load.files"), false, myProject);
    return mergeDataRef.get();
  }

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    try {
      GitFileUtils.addFilesForce(myProject, GitUtil.getRepositoryForFile(myProject, file).getRoot(), Collections.singletonList(file));
    }
    catch (VcsException e) {
      LOG.error("Confirming conflict resolution failed", e);
    }
  }

  @Override
  public boolean isBinary(@NotNull VirtualFile file) {
    return file.getFileType().isBinary();
  }

  @Override
  @NotNull
  public MergeSession createMergeSession(@NotNull List<VirtualFile> files) {
    return new MyMergeSession(files);
  }

  @Override
  public MergeDialogCustomizer createDefaultMergeDialogCustomizer() {
    return new GitDefaultMergeDialogCustomizer(myProject);
  }

  @NotNull
  public static String calcColumnName(boolean isTheirs, @Nullable String branchName) {
    String title = isTheirs ? GitBundle.message("merge.tool.column.theirs.status") : GitBundle.message("merge.tool.column.yours.status");
    return branchName != null
           ? title + " (" + branchName + ")"
           : title;
  }

  /**
   * The merge session, it queries conflict information.
   */
  private class MyMergeSession implements MergeSessionEx {
    private final Map<VirtualFile, GitConflict> myConflicts = new HashMap<>();
    private final String currentBranchName;
    private final String mergeHeadBranchName;

    MyMergeSession(List<VirtualFile> filesToMerge) {
      try {
        Map<VirtualFile, List<VirtualFile>> filesByRoot = GitUtil.sortFilesByGitRoot(myProject, filesToMerge);
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : filesByRoot.entrySet()) {
          VirtualFile root = e.getKey();
          List<VirtualFile> files = e.getValue();

          List<GitConflict> conflicts = GitMergeUtil.getConflicts(myProject, root, myReverseRoots.contains(root));

          Map<FilePath, GitConflict> map = ContainerUtil.newMapFromValues(conflicts.iterator(), it -> it.getFilePath());

          for (VirtualFile f : files) {
            FilePath filePath = VcsUtil.getFilePath(f);
            GitConflict conflict = map.get(filePath);
            if (conflict == null) {
              LOG.error(String.format("The conflict not found for file: %s (root: %s)", f.getPath(), root.getPath()));
              continue;
            }

            myConflicts.put(f, conflict);
          }
        }
        currentBranchName = GitDefaultMergeDialogCustomizerKt.getSingleCurrentBranchName(myProject, filesByRoot.keySet());
        mergeHeadBranchName = GitDefaultMergeDialogCustomizerKt.getSingleMergeBranchName(myProject, filesByRoot.keySet());
      }
      catch (VcsException ex) {
        throw new IllegalStateException("The git operation should not fail in this context", ex);
      }
    }

    @NotNull
    @Override
    public ColumnInfo[] getMergeInfoColumns() {
      return new ColumnInfo[]{new StatusColumn(false, currentBranchName), new StatusColumn(true, mergeHeadBranchName)};
    }

    @Override
    public boolean canMerge(@NotNull VirtualFile file) {
      GitConflict c = myConflicts.get(file);
      return c != null && !file.isDirectory();
    }

    @Override
    public void conflictResolvedForFile(@NotNull VirtualFile file, @NotNull Resolution resolution) {
      conflictResolvedForFiles(Collections.singletonList(file), resolution);
    }

    @Override
    public void conflictResolvedForFiles(@NotNull List<VirtualFile> files, @NotNull Resolution resolution) {
      if (resolution != Resolution.Merged) return;

      MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<GitConflict> conflicts = byRoot.get(root);

        List<FilePath> toAdd = new ArrayList<>();
        for (GitConflict c : conflicts) {
          toAdd.add(c.getFilePath());
        }

        try {
          GitFileUtils.addPathsForce(myProject, root, toAdd);
        }
        catch (VcsException e) {
          LOG.error(String.format("Unexpected exception during the git operation: modified - %s)", toAdd), e);
        }
      }
    }

    @Override
    public void acceptFilesRevisions(@NotNull List<VirtualFile> files, @NotNull Resolution resolution) throws VcsException {
      assert resolution == Resolution.AcceptedYours || resolution == Resolution.AcceptedTheirs;

      MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<GitConflict> conflicts = byRoot.get(root);
        boolean isReversed = myReverseRoots.contains(root);
        boolean acceptYours = !isReversed ? resolution == Resolution.AcceptedYours
                                          : resolution == Resolution.AcceptedTheirs;
        ConflictSide conflictSide = acceptYours ? ConflictSide.OURS : ConflictSide.THEIRS;

        GitMergeUtil.acceptOneVersion(myProject, root, conflicts, conflictSide);
      }
    }

    @NotNull
    private MultiMap<VirtualFile, GitConflict> groupConflictsByRoot(@NotNull List<VirtualFile> files) {
      MultiMap<VirtualFile, GitConflict> byRoot = MultiMap.create();
      for (VirtualFile file: files) {
        GitConflict c = myConflicts.get(file);
        if (c == null) {
          LOG.error("Conflict was not loaded for the file: " + file.getPath());
          continue;
        }

        byRoot.putValue(c.getRoot(), c);
      }
      return byRoot;
    }

    /**
     * The column shows either "yours" or "theirs" status
     */
    private class StatusColumn extends ColumnInfo<VirtualFile, String> {
      private final boolean myIsLast;

      StatusColumn(boolean isLast, @Nullable String branchName) {
        super(calcColumnName(isLast, branchName));
        myIsLast = isLast;
      }

      @Override
      public String valueOf(VirtualFile file) {
        GitConflict c = myConflicts.get(file);
        if (c == null) {
          LOG.error("No conflict for the file " + file);
          return "";
        }
        GitConflict.Status currentStatus = !c.isReversed() ? c.getStatus(ConflictSide.OURS) : c.getStatus(ConflictSide.THEIRS);
        GitConflict.Status lastStatus = c.isReversed() ? c.getStatus(ConflictSide.OURS) : c.getStatus(ConflictSide.THEIRS);
        GitConflict.Status status = myIsLast ? lastStatus : currentStatus;
        switch (status) {
          case MODIFIED:
            return GitBundle.message("merge.tool.column.status.modified");
          case DELETED:
            return GitBundle.message("merge.tool.column.status.deleted");
          default:
            throw new IllegalStateException("Unknown status " + status + " for file " + file.getPath());
        }
      }

      @Override
      public String getMaxStringValue() {
        return GitBundle.message("merge.tool.column.status.modified");
      }

      @Override
      public int getAdditionalWidth() {
        return 10;
      }
    }
  }
}
