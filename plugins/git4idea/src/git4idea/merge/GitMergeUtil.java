// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtilRt;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitConflict;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static git4idea.GitUtil.CHERRY_PICK_HEAD;
import static git4idea.GitUtil.MERGE_HEAD;

/**
 * Utilities for merge
 */
public class GitMergeUtil {
  private static final Logger LOG = Logger.getInstance(GitMergeUtil.class);

  static final int ORIGINAL_REVISION_NUM = 1; // common parent
  static final int YOURS_REVISION_NUM = 2; // file content on the local branch: "Yours"
  static final int THEIRS_REVISION_NUM = 3; // remote file content: "Theirs"

  /**
   * The item representing default strategy
   */
  public static final String DEFAULT_STRATEGY = GitBundle.getString("merge.default.strategy");

  /**
   * A private constructor for utility class
   */
  private GitMergeUtil() {
  }


  /**
   * Get a list of merge strategies for the specified branch count
   *
   * @param branchCount a number of branches to merge
   * @return an array of strategy names
   */
  @NonNls
  public static String[] getMergeStrategies(int branchCount) {
    if (branchCount < 0) {
      throw new IllegalArgumentException("Branch count must be non-negative: " + branchCount);
    }
    switch (branchCount) {
      case 0:
        return new String[]{DEFAULT_STRATEGY};
      case 1:
        return new String[]{DEFAULT_STRATEGY, "resolve", "recursive", "octopus", "ours", "subtree"};
      default:
        return new String[]{DEFAULT_STRATEGY, "octopus", "ours"};
    }
  }

  /**
   * Setup strategies combobox. The set of strategies changes according to amount of selected elements in branchChooser.
   *
   * @param branchChooser a branch chooser
   * @param strategy      a strategy selector
   */
  public static void setupStrategies(final ElementsChooser<String> branchChooser, final JComboBox strategy) {
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      private void updateStrategies(final List<String> elements) {
        strategy.removeAllItems();
        for (String s : getMergeStrategies(elements.size())) {
          strategy.addItem(s);
        }
        strategy.setSelectedItem(DEFAULT_STRATEGY);
      }

      @Override
      public void elementMarkChanged(final String element, final boolean isMarked) {
        final List<String> elements = branchChooser.getMarkedElements();
        if (elements.size() == 0) {
          strategy.setEnabled(false);
          updateStrategies(elements);
        }
        else {
          strategy.setEnabled(true);
          updateStrategies(elements);
        }
      }
    };
    listener.elementMarkChanged(null, true);
    branchChooser.addElementsMarkListener(listener);
  }

  public static MergeData loadMergeData(@NotNull Project project,
                                        @NotNull VirtualFile root,
                                        @NotNull FilePath path,
                                        boolean isReversed) throws VcsException {
    byte[] originalContent = loadOriginalContent(project, root, path);
    byte[] yoursContent = loadRevisionCatchingErrors(project, root, path, YOURS_REVISION_NUM);
    byte[] theirsContent = loadRevisionCatchingErrors(project, root, path, THEIRS_REVISION_NUM);

    // TODO: can be done once for a root
    GitRevisionNumber yoursRevision = resolveHead(project, root);
    GitRevisionNumber theirsRevision = resolveMergeHead(project, root);
    GitRevisionNumber originalRevision = findOriginalRevisionNumber(project, root, yoursRevision, theirsRevision);


    Trinity<String, String, String> blobs = getAffectedBlobs(project, root, path);

    FilePath originalPath = getBlobPathInRevision(project, root, path, blobs.getFirst(), originalRevision);
    FilePath yoursPath = getBlobPathInRevision(project, root, path, blobs.getSecond(), yoursRevision);
    FilePath theirsPath = getBlobPathInRevision(project, root, path, blobs.getThird(), theirsRevision);


    MergeData mergeData = new MergeData();

    mergeData.ORIGINAL = originalContent;
    mergeData.CURRENT = !isReversed ? yoursContent : theirsContent;
    mergeData.LAST = isReversed ? yoursContent : theirsContent;

    mergeData.ORIGINAL_REVISION_NUMBER = originalRevision;
    mergeData.CURRENT_REVISION_NUMBER = !isReversed ? yoursRevision : theirsRevision;
    mergeData.LAST_REVISION_NUMBER = isReversed ? yoursRevision : theirsRevision;

    mergeData.ORIGINAL_FILE_PATH = originalPath;
    mergeData.CURRENT_FILE_PATH = !isReversed ? yoursPath : theirsPath;
    mergeData.LAST_FILE_PATH = isReversed ? yoursPath : theirsPath;

    return mergeData;
  }


  @Nullable
  private static GitRevisionNumber findOriginalRevisionNumber(@NotNull Project project,
                                                              @NotNull VirtualFile root,
                                                              @Nullable VcsRevisionNumber yoursRevision,
                                                              @Nullable VcsRevisionNumber theirsRevision) {
    if (yoursRevision == null || theirsRevision == null) return null;
    try {
      return GitHistoryUtils.getMergeBase(project, root, yoursRevision.asString(), theirsRevision.asString());
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  private static GitRevisionNumber resolveMergeHead(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(project, root, MERGE_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the MERGE_HEAD in " + root + ": " + e.getMessage()); // this may be not a bug, just cherry-pick
    }

    try {
      return GitRevisionNumber.resolve(project, root, CHERRY_PICK_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the CHERRY_PICK_HEAD in " + root + ": " + e.getMessage());
    }

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    assert repository != null;

    File rebaseApply = repository.getRepositoryFiles().getRebaseApplyDir();
    GitRevisionNumber rebaseRevision = readRevisionFromFile(project, root, new File(rebaseApply, "original-commit"));
    if (rebaseRevision != null) return rebaseRevision;

    File rebaseMerge = repository.getRepositoryFiles().getRebaseMergeDir();
    GitRevisionNumber mergeRevision = readRevisionFromFile(project, root, new File(rebaseMerge, "stopped-sha"));
    if (mergeRevision != null) return mergeRevision;

    return null;
  }

  @Nullable
  private static GitRevisionNumber readRevisionFromFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull File file) {
    if (!file.exists()) return null;
    String revision = DvcsUtil.tryLoadFileOrReturn(file, null, CharsetToolkit.UTF8);
    if (revision == null) return null;

    try {
      return GitRevisionNumber.resolve(project, root, revision);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve revision  '" + revision + "' in " + root + ": " + e.getMessage());
      return null;
    }
  }

  @Nullable
  private static GitRevisionNumber resolveHead(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(project, root, "HEAD");
    }
    catch (VcsException e) {
      LOG.error("Couldn't resolve the HEAD in " + root, e);
      return null;
    }
  }

  @NotNull
  private static byte[] loadOriginalContent(@NotNull Project project,
                                            @NotNull VirtualFile root,
                                            @NotNull FilePath path) {
    try {
      return loadRevisionContent(project, root, path, ORIGINAL_REVISION_NUM);
    }
    catch (Exception ex) {
      /// unable to load original revision, use the current instead
      /// This could happen in case if rebasing.
      try {
        return ReadAction.compute(() -> {
          VirtualFile file = path.getVirtualFile();
          if (file == null || !file.isValid()) {
            LOG.debug("File not found: " + path);
            return ArrayUtilRt.EMPTY_BYTE_ARRAY;
          }

          return file.contentsToByteArray();
        });
      }
      catch (IOException e) {
        LOG.error(e);
        return ArrayUtilRt.EMPTY_BYTE_ARRAY;
      }
    }
  }

  @NotNull
  private static byte[] loadRevisionCatchingErrors(@NotNull Project project,
                                                   @NotNull VirtualFile root,
                                                   @NotNull FilePath path,
                                                   int stageNum) throws VcsException {
    try {
      return loadRevisionContent(project, root, path, stageNum);
    }
    catch (VcsException e) {
      String m = e.getMessage().trim();
      if (m.startsWith("fatal: ambiguous argument ")
          || (m.startsWith("fatal: Path '") && m.contains("' exists on disk, but not in '"))
          || m.contains("is in the index, but not at stage ")
          || m.contains("bad revision")
          || m.startsWith("fatal: Not a valid object name")) {
        return ArrayUtilRt.EMPTY_BYTE_ARRAY;
      }
      else {
        throw e;
      }
    }
  }

  @NotNull
  private static byte[] loadRevisionContent(@NotNull Project project,
                                            @NotNull VirtualFile root,
                                            @NotNull FilePath path,
                                            int stageNum) throws VcsException {
    return GitFileUtils.getFileContent(project, root, ":" + stageNum, VcsFileUtil.relativePath(root, path));
  }


  @NotNull
  private static Trinity<String, String, String> getAffectedBlobs(@NotNull Project project,
                                                                  @NotNull VirtualFile root,
                                                                  @NotNull FilePath path) {
    try {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_FILES);
      h.addParameters("--exclude-standard", "--unmerged", "-z");
      h.endOptions();
      h.addRelativePaths(path);

      String output = Git.getInstance().runCommand(h).getOutputOrThrow();
      StringScanner s = new StringScanner(output);

      String originalBlob = null;
      String yoursBlob = null;
      String theirsBlob = null;

      while (s.hasMoreData()) {
        s.spaceToken(); // permissions
        String blob = s.spaceToken();
        int source = Integer.parseInt(s.tabToken()); // stage
        s.boundedToken('\u0000'); // file name

        if (source == ORIGINAL_REVISION_NUM) {
          originalBlob = blob;
        }
        else if (source == YOURS_REVISION_NUM) {
          yoursBlob = blob;
        }
        else if (source == THEIRS_REVISION_NUM) {
          theirsBlob = blob;
        }

        else {
          throw new IllegalStateException("Unknown revision " + source + " for the file: " + path);
        }
      }
      return Trinity.create(originalBlob, yoursBlob, theirsBlob);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return Trinity.create(null, null, null);
    }
  }

  @Nullable
  private static FilePath getBlobPathInRevision(@NotNull Project project,
                                                @NotNull VirtualFile root,
                                                @NotNull FilePath path,
                                                @Nullable String blob,
                                                @Nullable VcsRevisionNumber revision) {
    if (blob == null || revision == null) return null;

    // fast check if file was not renamed
    FilePath revisionPath = doGetBlobPathInRevision(project, root, blob, revision, path);
    if (revisionPath != null) return revisionPath;

    return doGetBlobPathInRevision(project, root, blob, revision, null);
  }

  @Nullable
  private static FilePath doGetBlobPathInRevision(@NotNull Project project,
                                                  @NotNull final VirtualFile root,
                                                  @NotNull final String blob,
                                                  @NotNull VcsRevisionNumber revision,
                                                  @Nullable FilePath path) {
    final FilePath[] result = new FilePath[1];
    final boolean[] pathAmbiguous = new boolean[1];

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());

    if (path != null) {
      h.endOptions();
      h.addRelativePaths(path);
    }
    else {
      h.addParameters("-r");
      h.endOptions();
    }

    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        if (!line.contains(blob)) return;
        if (pathAmbiguous[0]) return;

        GitIndexUtil.StagedFileOrDirectory stagedFile = GitIndexUtil.parseListTreeRecord(root, line);
        if (stagedFile instanceof GitIndexUtil.StagedFile && blob.equals(((GitIndexUtil.StagedFile)stagedFile).getBlobHash())) {
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

  public static void acceptOneVersion(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull Collection<? extends GitConflict> conflicts,
                                      @NotNull GitConflict.ConflictSide side) throws VcsException {
    boolean isCurrent = side == GitConflict.ConflictSide.OURS;

    for (GitConflict conflict : conflicts) {
      assert root.equals(conflict.getRoot());
    }

    List<FilePath> toCheckout = new ArrayList<>();

    for (GitConflict conflict : conflicts) {
      GitConflict.Status status = conflict.getStatus(side);
      FilePath filePath = conflict.getFilePath();

      if (status != GitConflict.Status.DELETED) {
        toCheckout.add(filePath);
      }
    }

    for (List<String> paths : VcsFileUtil.chunkPaths(root, toCheckout)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHECKOUT);
      handler.addParameters(isCurrent ? "--ours" : "--theirs");
      handler.endOptions();
      handler.addParameters(paths);

      GitCommandResult result = Git.getInstance().runCommand(handler);
      if (!result.success()) throw new VcsException(result.getErrorOutputAsJoinedString());
    }
  }

  /**
   *
   */
  public static void markConflictResolved(@NotNull Project project,
                                          @NotNull VirtualFile root,
                                          @NotNull Collection<? extends GitConflict> conflicts,
                                          @Nullable GitConflict.ConflictSide side) throws VcsException {
    List<FilePath> toAdd = new ArrayList<>();
    List<FilePath> toDelete = new ArrayList<>();

    for (GitConflict conflict : conflicts) {
      FilePath filePath = conflict.getFilePath();

      if (side == null || conflict.getStatus(side) != GitConflict.Status.DELETED) {
        toAdd.add(filePath);
      }
      else {
        toDelete.add(filePath);
      }
    }

    GitFileUtils.addPathsForce(project, root, toAdd);
    GitFileUtils.deletePaths(project, root, toDelete);
  }

  public static boolean isReverseRoot(@NotNull GitRepository repository) {
    return repository.getState().equals(GitRepository.State.REBASING);
  }
}
