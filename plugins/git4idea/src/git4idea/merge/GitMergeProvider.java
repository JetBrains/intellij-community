// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitConflict;
import git4idea.repo.GitConflict.ConflictSide;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.merge.GitMergeUtil.*;

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
    Set<VirtualFile> reverseMap = new HashSet<>();
    for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
      boolean reverse;
      if (reverseOrDetect == ReverseRequest.DETECT) {
        reverse = isReverseRoot(repository);
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
    VirtualFile root = GitUtil.getRepositoryForFile(myProject, file).getRoot();
    FilePath path = VcsUtil.getFilePath(file);
    return loadMergeData(myProject, root, path, myReverseRoots.contains(root));
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
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> new MyMergeSession(files), "Loading Unmerged Files", true, myProject);
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
   * The conflict descriptor
   */
  private static class Conflict {
    VirtualFile myFile;
    VirtualFile myRoot;
    GitConflict.Status myStatusTheirs;
    GitConflict.Status myStatusYours;
  }

  /**
   * The merge session, it queries conflict information.
   */
  private class MyMergeSession implements MergeSessionEx {
    private final Map<VirtualFile, GitConflict> myConflicts = new HashMap<>();
    private final String currentBranchName;
    private final String mergeHeadBranchName;

    MyMergeSession(List<VirtualFile> filesToMerge) {
      // get conflict type by the file
      try {
        Map<GitRepository, List<VirtualFile>> filesByRoot = GitUtil.sortFilesByRepository(myProject, filesToMerge);
        for (Map.Entry<GitRepository, List<VirtualFile>> e : filesByRoot.entrySet()) {
          Map<String, Conflict> cs = new HashMap<>();
          VirtualFile root = e.getKey().getRoot();
          List<VirtualFile> files = e.getValue();
          GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.LS_FILES);
          h.setStdoutSuppressed(true);
          h.setSilent(true);
          h.addParameters("--exclude-standard", "--unmerged", "-t", "-z");
          h.endOptions();
          String output = Git.getInstance().runCommand(h).getOutputOrThrow();
          StringScanner s = new StringScanner(output);
          while (s.hasMoreData()) {
            if (!"M".equals(s.spaceToken())) {
              s.boundedToken('\u0000');
              continue;
            }
            s.spaceToken(); // permissions
            s.spaceToken(); // commit hash
            int source = Integer.parseInt(s.tabToken());
            String file = s.boundedToken('\u0000');
            Conflict c = cs.get(file);
            if (c == null) {
              c = new Conflict();
              c.myRoot = root;
              cs.put(file, c);
            }
            if (source == THEIRS_REVISION_NUM) {
              c.myStatusTheirs = GitConflict.Status.MODIFIED;
            }
            else if (source == YOURS_REVISION_NUM) {
              c.myStatusYours = GitConflict.Status.MODIFIED;
            }
            else if (source != ORIGINAL_REVISION_NUM) {
              throw new IllegalStateException("Unknown revision " + source + " for the file: " + file);
            }
          }

          for (VirtualFile f : files) {
            String path = VcsFileUtil.relativePath(root, f);
            Conflict c = cs.get(path);
            if (c == null) {
              LOG.error(String.format("The conflict not found for file: %s(%s)%nFull ls-files output: %n%s%nAll files: %n%s",
                                      f.getPath(), path, output, files));
              continue;
            }
            c.myFile = f;
            if (c.myStatusTheirs == null) {
              c.myStatusTheirs = GitConflict.Status.DELETED;
            }
            if (c.myStatusYours == null) {
              c.myStatusYours = GitConflict.Status.DELETED;
            }
            myConflicts.put(f, new GitConflict(root, VcsUtil.getFilePath(f), c.myStatusYours, c.myStatusTheirs));
          }
        }
        currentBranchName = GitDefaultMergeDialogCustomizerKt.getSingleCurrentBranchName(filesByRoot.keySet());
        mergeHeadBranchName = GitDefaultMergeDialogCustomizerKt.getSingleMergeBranchName(filesByRoot.keySet());
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
    public void conflictResolvedForFiles(@NotNull List<? extends VirtualFile> files, @NotNull Resolution resolution) {
      MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<GitConflict> conflicts = byRoot.get(root);
        ConflictSide resolutionSide = resolution != Resolution.Merged ? getAcceptedConflictSide(resolution, root) : null;

        try {
          markConflictResolved(myProject, root, conflicts, resolutionSide);
        }
        catch (VcsException e) {
          LOG.error(String.format("Unexpected exception during the git operation. Files - %s",
                                  ContainerUtil.map(conflicts, GitConflict::getFilePath)), e);
        }
      }
    }

    @Override
    public void acceptFilesRevisions(@NotNull List<? extends VirtualFile> files, @NotNull Resolution resolution) throws VcsException {
      assert resolution == Resolution.AcceptedYours || resolution == Resolution.AcceptedTheirs;

      MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<GitConflict> conflicts = byRoot.get(root);
        ConflictSide conflictSide = getAcceptedConflictSide(resolution, root);

        acceptOneVersion(myProject, root, conflicts, conflictSide);
      }
    }

    @NotNull
    private ConflictSide getAcceptedConflictSide(@NotNull Resolution resolution, @NotNull VirtualFile root) {
      assert resolution == Resolution.AcceptedYours || resolution == Resolution.AcceptedTheirs;
      boolean isReversed = myReverseRoots.contains(root);
      boolean acceptYours = !isReversed ? resolution == Resolution.AcceptedYours
                                        : resolution == Resolution.AcceptedTheirs;
      return acceptYours ? ConflictSide.OURS : ConflictSide.THEIRS;
    }

    @NotNull
    private MultiMap<VirtualFile, GitConflict> groupConflictsByRoot(@NotNull List<? extends VirtualFile> files) {
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
        boolean isReversed = myReverseRoots.contains(c.getRoot());
        GitConflict.Status currentStatus = c.getStatus(ConflictSide.OURS, isReversed);
        GitConflict.Status lastStatus = c.getStatus(ConflictSide.THEIRS, isReversed);
        GitConflict.Status status = myIsLast ? lastStatus : currentStatus;
        switch (status) {
          case ADDED:
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
