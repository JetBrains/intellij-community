// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.*;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.commands.*;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
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

/**
 * Merge-changes provider for Git, used by IDEA internal 3-way merge tool
 */
public class GitMergeProvider implements MergeProvider2 {
  private static final int ORIGINAL_REVISION_NUM = 1; // common parent
  private static final int YOURS_REVISION_NUM = 2; // file content on the local branch: "Yours"
  private static final int THEIRS_REVISION_NUM = 3; // remote file content: "Theirs"

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
    final MergeData mergeData = new MergeData();
    final VirtualFile root = GitUtil.getGitRoot(file);
    final FilePath path = VcsUtil.getFilePath(file.getPath());

    VcsRunnable runnable = new VcsRunnable() {
      @Override
      @SuppressWarnings({"ConstantConditions"})
      public void run() throws VcsException {
        GitFileRevision original = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + ORIGINAL_REVISION_NUM));
        GitFileRevision current = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + yoursRevision(root)));
        GitFileRevision last = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + theirsRevision(root)));
        try {
          mergeData.ORIGINAL = original.getContent();
        }
        catch (Exception ex) {
          /// unable to load original revision, use the current instead
          /// This could happen in case if rebasing.
          try {
            mergeData.ORIGINAL = file.contentsToByteArray();
          }
          catch (IOException e) {
            LOG.error(e);
            mergeData.ORIGINAL = ArrayUtil.EMPTY_BYTE_ARRAY;
          }
        }
        mergeData.CURRENT = loadRevisionCatchingErrors(current);
        mergeData.LAST = loadRevisionCatchingErrors(last);

        // TODO: can be done once for a root
        mergeData.CURRENT_REVISION_NUMBER = findCurrentRevisionNumber(root);
        mergeData.LAST_REVISION_NUMBER = findLastRevisionNumber(root);
        mergeData.ORIGINAL_REVISION_NUMBER = findOriginalRevisionNumber(root, mergeData.CURRENT_REVISION_NUMBER, mergeData.LAST_REVISION_NUMBER);


        Trinity<String, String, String> blobs = getAffectedBlobs(root, file);

        mergeData.CURRENT_FILE_PATH = getBlobPathInRevision(root, file, blobs.getFirst(), mergeData.CURRENT_REVISION_NUMBER);
        mergeData.ORIGINAL_FILE_PATH = getBlobPathInRevision(root, file, blobs.getSecond(), mergeData.ORIGINAL_REVISION_NUMBER);
        mergeData.LAST_FILE_PATH = getBlobPathInRevision(root, file, blobs.getThird(), mergeData.LAST_REVISION_NUMBER);
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, GitBundle.message("merge.load.files"), false, myProject);
    return mergeData;
  }

  @NotNull
  private Trinity<String, String, String> getAffectedBlobs(@NotNull VirtualFile root, @NotNull VirtualFile file) {
    try {
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.LS_FILES);
      h.addParameters("--exclude-standard", "--unmerged", "-z");
      h.endOptions();
      h.addRelativeFiles(Collections.singleton(file));

      String output = Git.getInstance().runCommand(h).getOutputOrThrow();
      StringScanner s = new StringScanner(output);

      String lastBlob = null;
      String currentBlob = null;
      String originalBlob = null;

      while (s.hasMoreData()) {
        s.spaceToken(); // permissions
        String blob = s.spaceToken();
        int source = Integer.parseInt(s.tabToken()); // stage
        s.boundedToken('\u0000'); // file name

        if (source == theirsRevision(root)) {
          lastBlob = blob;
        }
        else if (source == yoursRevision(root)) {
          currentBlob = blob;
        }
        else if (source == ORIGINAL_REVISION_NUM) {
          originalBlob = blob;
        }
        else {
          throw new IllegalStateException("Unknown revision " + source + " for the file: " + file);
        }
      }
      return Trinity.create(currentBlob, originalBlob, lastBlob);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return Trinity.create(null, null, null);
    }
  }

  @Nullable
  private FilePath getBlobPathInRevision(@NotNull VirtualFile root,
                                         @NotNull VirtualFile file,
                                         @Nullable String blob,
                                         @Nullable VcsRevisionNumber revision) {
    if (blob == null || revision == null) return null;

    // fast check if file was not renamed
    FilePath path = doGetBlobPathInRevision(root, blob, revision, file);
    if (path != null) return path;

    return doGetBlobPathInRevision(root, blob, revision, null);
  }

  @Nullable
  private FilePath doGetBlobPathInRevision(@NotNull final VirtualFile root,
                                           @NotNull final String blob,
                                           @NotNull VcsRevisionNumber revision,
                                           @Nullable VirtualFile file) {
    final FilePath[] result = new FilePath[1];
    final boolean[] pathAmbiguous = new boolean[1];

    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());

    if (file != null) {
      h.endOptions();
      h.addRelativeFiles(Collections.singleton(file));
    }
    else {
      h.addParameters("-r");
      h.endOptions();
    }

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        if (!line.contains(blob)) return;
        if (pathAmbiguous[0]) return;

        GitIndexUtil.StagedFileOrDirectory stagedFile = GitIndexUtil.parseListTreeRecord(root, line);
        if (stagedFile instanceof GitIndexUtil.StagedFile && blob.equals(((GitIndexUtil.StagedFile) stagedFile).getBlobHash())) {
          if (result[0] == null) {
            result[0] = stagedFile.getPath();
          }
          else {
            // there are multiple files with given content in this revision.
            // we don't know which is right, so do not return any
            pathAmbiguous[0] = true;
          }
        }
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h);

    if (pathAmbiguous[0]) return null;
    return result[0];
  }

  @Nullable
  private GitRevisionNumber findLastRevisionNumber(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? resolveHead(root) : resolveMergeHead(root);
  }

  @Nullable
  private GitRevisionNumber findCurrentRevisionNumber(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? resolveMergeHead(root) : resolveHead(root);
  }

  @Nullable
  private GitRevisionNumber findOriginalRevisionNumber(@NotNull VirtualFile root,
                                                       @Nullable VcsRevisionNumber currentRevision,
                                                       @Nullable VcsRevisionNumber lastRevision) {
    if (currentRevision == null || lastRevision == null) return null;
    try {
      return GitHistoryUtils.getMergeBase(myProject, root, currentRevision.asString(), lastRevision.asString());
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  private GitRevisionNumber resolveMergeHead(@NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(myProject, root, MERGE_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the MERGE_HEAD in " + root + ": " + e.getMessage()); // this may be not a bug, just cherry-pick
    }

    try {
      return GitRevisionNumber.resolve(myProject, root, CHERRY_PICK_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the CHERRY_PICK_HEAD in " + root + ": " + e.getMessage());
    }

    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
    assert repository != null;

    File rebaseApply = repository.getRepositoryFiles().getRebaseApplyDir();
    GitRevisionNumber rebaseRevision = readRevisionFromFile(root, new File(rebaseApply, "original-commit"));
    if (rebaseRevision != null) return rebaseRevision;

    File rebaseMerge = repository.getRepositoryFiles().getRebaseMergeDir();
    GitRevisionNumber mergeRevision = readRevisionFromFile(root, new File(rebaseMerge, "stopped-sha"));
    if (mergeRevision != null) return mergeRevision;

    return null;
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

  @Nullable
  private GitRevisionNumber readRevisionFromFile(@NotNull VirtualFile root, @NotNull File file) {
    if (!file.exists()) return null;
    String revision = DvcsUtil.tryLoadFileOrReturn(file, null, CharsetToolkit.UTF8);
    if (revision == null) return null;

    try {
      return GitRevisionNumber.resolve(myProject, root, revision);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve revision  '" + revision + "' in " + root + ": " + e.getMessage());
      return null;
    }
  }

  @Nullable
  private GitRevisionNumber resolveHead(@NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(myProject, root, "HEAD");
    }
    catch (VcsException e) {
      LOG.error("Couldn't resolve the HEAD in " + root, e);
      return null;
    }
  }

  private static byte[] loadRevisionCatchingErrors(@NotNull GitFileRevision revision) throws VcsException {
    try {
      return revision.getContent();
    } catch (VcsException e) {
      String m = e.getMessage().trim();
      if (m.startsWith("fatal: ambiguous argument ")
          || (m.startsWith("fatal: Path '") && m.contains("' exists on disk, but not in '"))
          || m.contains("is in the index, but not at stage ")
          || m.contains("bad revision")
          || m.startsWith("fatal: Not a valid object name")) {
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
      else {
        throw e;
      }
    }
  }

  /**
   * @return number for "yours" revision  (taking {@code reverse} flag in account)
   * @param root
   */
  private int yoursRevision(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? THEIRS_REVISION_NUM : YOURS_REVISION_NUM;
  }

  /**
   * @return number for "theirs" revision (taking {@code reverse} flag in account)
   * @param root
   */
  private int theirsRevision(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? YOURS_REVISION_NUM : THEIRS_REVISION_NUM;
  }

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    try {
      GitFileUtils.addFiles(myProject, GitUtil.getGitRoot(file), file);
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
  public void mergeDone(@NotNull List<VirtualFile> files) {
    for (GitRepository repository : GitUtil.getRepositoriesForFiles(myProject, files)) {
      repository.update();
    }
  }

  @Override
  public MergeDialogCustomizer createDefaultMergeDialogCustomizer() {
    return new GitDefaultMergeDialogCustomizer(this);
  }

  private static String calcName(boolean isTheirs, @Nullable String branchName) {
    String title = isTheirs ? GitBundle.message("merge.tool.column.theirs.status") : GitBundle.message("merge.tool.column.yours.status");
    return branchName != null
           ? title + " (" + StringUtil.shortenTextWithEllipsis(branchName, 15, 7, true) + ")"
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
        Map<VirtualFile, List<VirtualFile>> filesByRoot = GitUtil.sortFilesByGitRoot(filesToMerge);
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
            if (source == theirsRevision(root)) {
              c.myStatusTheirs = Conflict.Status.MODIFIED;
            }
            else if (source == yoursRevision(root)) {
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
          Conflict.Status status;
          switch (resolution) {
            case AcceptedTheirs:
              status = c.myStatusTheirs;
              break;
            case AcceptedYours:
              status = c.myStatusYours;
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
          GitFileUtils.addFiles(myProject, root, toAdd);
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
      boolean isCurrent = resolution == Resolution.AcceptedYours;

      for (VirtualFile root : byRoot.keySet()) {
        Collection<Conflict> conflicts = byRoot.get(root);

        List<VirtualFile> filesToCheckout = ContainerUtil.mapNotNull(conflicts, c -> {
          Conflict.Status status = isCurrent ? c.myStatusYours : c.myStatusTheirs;
          return status == Conflict.Status.MODIFIED ? c.myFile : null;
        });

        String parameter = myReverseRoots.contains(root)
                           ? isCurrent ? "--theirs" : "--ours"
                           : isCurrent ? "--ours" : "--theirs";

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
      /**
       * if false, "yours" status is displayed, otherwise "theirs"
       */
      private final boolean myIsTheirs;

      public StatusColumn(boolean isTheirs, @Nullable String branchName) {
        super(calcName(isTheirs, branchName));
        myIsTheirs = isTheirs;
      }

      @Override
      public String valueOf(VirtualFile file) {
        Conflict c = myConflicts.get(file);
        if (c == null) {
          LOG.error("No conflict for the file " + file);
          return "";
        }
        Conflict.Status s = myIsTheirs ? c.myStatusTheirs : c.myStatusYours;
        switch (s) {
          case MODIFIED:
            return GitBundle.message("merge.tool.column.status.modified");
          case DELETED:
            return GitBundle.message("merge.tool.column.status.deleted");
          default:
            throw new IllegalStateException("Unknown status " + s + " for file " + file.getPath());
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
