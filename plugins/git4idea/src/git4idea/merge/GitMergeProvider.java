// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import git4idea.util.StringScanner;
import one.util.streamex.MoreCollectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static git4idea.GitUtil.CHERRY_PICK_HEAD;
import static git4idea.GitUtil.MERGE_HEAD;
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
    Set<VirtualFile> reverseMap = ContainerUtil.newHashSet();
    for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
      boolean reverse;
      if (reverseOrDetect == ReverseRequest.DETECT) {
        reverse = repository.getState().equals(GitRepository.State.REBASING);
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

  @Nullable
  public String resolveMergeBranch(@NotNull VirtualFile file) {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(file);
    if (repository == null) {
      return null;
    }
    return resolveMergeBranch(repository);
  }

  @Nullable
  public String resolveMergeBranchOrCherryPick(@NotNull VirtualFile file) {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(file);
    if (repository == null) {
      return null;
    }
    String mergeBranch = resolveMergeBranch(repository);
    if (mergeBranch != null) {
      return mergeBranch;
    }
    String rebaseOntoBranch = resolveRebaseOntoBranch(repository.getRoot());
    if (rebaseOntoBranch != null) {
      return rebaseOntoBranch;
    }

    try {
      GitRevisionNumber.resolve(myProject, repository.getRoot(), CHERRY_PICK_HEAD);
      return "cherry-pick";
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Nullable
  public String resolveMergeBranch(GitRepository repository) {
    GitRevisionNumber mergeHeadRevisionNumber;
    try {
      mergeHeadRevisionNumber = GitRevisionNumber.resolve(myProject, repository.getRoot(), MERGE_HEAD);
    }
    catch (VcsException e) {
      return null;
    }
    return resolveBranchName(repository, mergeHeadRevisionNumber);
  }

  @Nullable
  public String resolveRebaseOntoBranch(@NotNull VirtualFile root) {
    File rebaseDir = GitRebaseUtils.getRebaseDir(myProject, root);
    if (rebaseDir == null) {
      return null;
    }
    String ontoHash;
    try {
      ontoHash = FileUtil.loadFile(new File(rebaseDir, "onto")).trim();
    }
    catch (IOException e) {
      return null;
    }
    GitRepository repo = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
    if (repo == null) {
      return null;
    }
    return resolveBranchName(repo, new GitRevisionNumber(ontoHash));
  }

  public static String resolveBranchName(GitRepository repository, GitRevisionNumber revisionNumber) {
    Hash hash = HashImpl.build(revisionNumber.asString());
    Collection<GitLocalBranch> localBranchesByHash = repository.getBranches().findLocalBranchesByHash(hash);
    if (localBranchesByHash.size() == 1) {
      return localBranchesByHash.iterator().next().getName();
    }
    if (localBranchesByHash.isEmpty()) {
      Collection<GitRemoteBranch> remoteBranchesByHash = repository.getBranches().findRemoteBranchesByHash(hash);
      if (remoteBranchesByHash.size() == 1) {
        return remoteBranchesByHash.iterator().next().getName();
      }
    }
    return revisionNumber.getShortRev();
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
    return new GitDefaultMergeDialogCustomizer(this);
  }

  @NotNull
  public static String calcColumnName(boolean isTheirs, @Nullable String branchName) {
    String title = isTheirs ? GitBundle.message("merge.tool.column.theirs.status") : GitBundle.message("merge.tool.column.yours.status");
    return branchName != null
           ? title + " (" + branchName + ")"
           : title;
  }

  @Nullable
  public String getSingleMergeBranchName(Collection<VirtualFile> roots) {
    return roots
      .stream()
      .map(root -> resolveMergeBranchOrCherryPick(root))
      .filter(branch -> branch != null)
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  @Nullable
  public String getSingleCurrentBranchName(Collection<VirtualFile> roots) {
    return roots
      .stream()
      .map(root -> GitRepositoryManager.getInstance(myProject).getRepositoryForFile(root))
      .map(repo -> repo == null ? null : repo.getCurrentBranchName())
      .filter(branch -> branch != null)
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  /**
   * The conflict descriptor
   */
  private static class Conflict {
    VirtualFile myFile;
    VirtualFile myRoot;
    Status myStatusTheirs;
    Status myStatusYours;

    enum Status {
      MODIFIED, // modified on the branch
      DELETED // deleted on the branch
    }
  }


  /**
   * The merge session, it queries conflict information.
   */
  private class MyMergeSession implements MergeSessionEx {
    Map<VirtualFile, Conflict> myConflicts = new HashMap<>();
    String currentBranchName;
    String mergeHeadBranchName;

    MyMergeSession(List<VirtualFile> filesToMerge) {
      // get conflict type by the file
      try {
        Map<VirtualFile, List<VirtualFile>> filesByRoot = GitUtil.sortFilesByGitRoot(myProject, filesToMerge);
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : filesByRoot.entrySet()) {
          Map<String, Conflict> cs = new HashMap<>();
          VirtualFile root = e.getKey();
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
              c.myStatusTheirs = Conflict.Status.MODIFIED;
            }
            else if (source == YOURS_REVISION_NUM) {
              c.myStatusYours = Conflict.Status.MODIFIED;
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
              c.myStatusTheirs = Conflict.Status.DELETED;
            }
            if (c.myStatusYours == null) {
              c.myStatusYours = Conflict.Status.DELETED;
            }
            myConflicts.put(f, c);
          }
        }
        currentBranchName = getSingleCurrentBranchName(filesByRoot.keySet());
        mergeHeadBranchName = getSingleMergeBranchName(filesByRoot.keySet());
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
      Conflict c = myConflicts.get(file);
      return c != null && !file.isDirectory();
    }

    @Override
    public void conflictResolvedForFile(@NotNull VirtualFile file, @NotNull Resolution resolution) {
      conflictResolvedForFiles(Collections.singletonList(file), resolution);
    }

    @Override
    public void conflictResolvedForFiles(@NotNull List<VirtualFile> files, @NotNull Resolution resolution) {
      MultiMap<VirtualFile, Conflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<Conflict> conflicts = byRoot.get(root);

        List<VirtualFile> toAdd = new ArrayList<>();
        List<VirtualFile> toDelete = new ArrayList<>();

        for (Conflict c: conflicts) {
          boolean isReversed = myReverseRoots.contains(c.myRoot);

          Conflict.Status status;
          switch (resolution) {
            case AcceptedTheirs:
              status = !isReversed ? c.myStatusTheirs : c.myStatusYours;
              break;
            case AcceptedYours:
              status = isReversed ? c.myStatusTheirs : c.myStatusYours;
              break;
            case Merged:
              status = Conflict.Status.MODIFIED;
              break;
            default:
              throw new IllegalArgumentException("Unsupported resolution: " + resolution);
          }

          if (status == Conflict.Status.MODIFIED) {
            toAdd.add(c.myFile);
          }
          else {
            toDelete.add(c.myFile);
          }
        }

        try {
          GitFileUtils.addFilesForce(myProject, root, toAdd);
          GitFileUtils.deleteFiles(myProject, root, toDelete);
        }
        catch (VcsException e) {
          LOG.error(String.format("Unexpected exception during the git operation: modified - %s deleted - %s)", toAdd, toDelete), e);
        }
      }
    }

    @Override
    public void acceptFilesRevisions(@NotNull List<VirtualFile> files, @NotNull Resolution resolution) throws VcsException {
      assert resolution == Resolution.AcceptedYours || resolution == Resolution.AcceptedTheirs;

      MultiMap<VirtualFile, Conflict> byRoot = groupConflictsByRoot(files);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<Conflict> conflicts = byRoot.get(root);
        boolean isReversed = myReverseRoots.contains(root);
        boolean acceptYours = !isReversed ? resolution == Resolution.AcceptedYours
                                          : resolution == Resolution.AcceptedTheirs;

        List<VirtualFile> filesToCheckout = ContainerUtil.mapNotNull(conflicts, c -> {
          Conflict.Status status = acceptYours ? c.myStatusYours : c.myStatusTheirs;
          return status == Conflict.Status.MODIFIED ? c.myFile : null;
        });

        String parameter = acceptYours ? "--ours" : "--theirs";

        for (List<String> paths : VcsFileUtil.chunkFiles(root, filesToCheckout)) {
          GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
          handler.addParameters(parameter);
          handler.endOptions();
          handler.addParameters(paths);
          GitCommandResult result = Git.getInstance().runCommand(handler);
          if (!result.success()) throw new VcsException(result.getErrorOutputAsJoinedString());
        }
      }
    }

    @NotNull
    private MultiMap<VirtualFile, Conflict> groupConflictsByRoot(@NotNull List<VirtualFile> files) {
      MultiMap<VirtualFile, Conflict> byRoot = MultiMap.create();
      for (VirtualFile file: files) {
        Conflict c = myConflicts.get(file);
        if (c == null) {
          LOG.error("Conflict was not loaded for the file: " + file.getPath());
          continue;
        }

        byRoot.putValue(c.myRoot, c);
      }
      return byRoot;
    }

    /**
     * The column shows either "yours" or "theirs" status
     */
    class StatusColumn extends ColumnInfo<VirtualFile, String> {
      private final boolean myIsLast;

      StatusColumn(boolean isLast, @Nullable String branchName) {
        super(calcColumnName(isLast, branchName));
        myIsLast = isLast;
      }

      @Override
      public String valueOf(VirtualFile file) {
        Conflict c = myConflicts.get(file);
        if (c == null) {
          LOG.error("No conflict for the file " + file);
          return "";
        }
        boolean isReversed = myReverseRoots.contains(c.myRoot);
        Conflict.Status currentStatus = !isReversed ? c.myStatusYours : c.myStatusTheirs;
        Conflict.Status lastStatus = isReversed ? c.myStatusYours : c.myStatusTheirs;
        Conflict.Status status = myIsLast ? lastStatus : currentStatus;
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
