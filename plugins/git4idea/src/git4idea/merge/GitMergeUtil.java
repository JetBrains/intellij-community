// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerListener;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
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

  /**
   * Show updates caused by git operation
   *
   * @param project     the context project
   * @param exceptions  the exception list
   * @param root        the git root
   * @param currentRev  the revision before update
   * @param beforeLabel the local history label before update
   * @param actionName  the action name
   * @param actionInfo  the information about the action
   */
  public static void showUpdates(final Project project,
                                 final List<VcsException> exceptions,
                                 final VirtualFile root,
                                 final GitRevisionNumber currentRev,
                                 final Label beforeLabel,
                                 final String actionName,
                                 final ActionInfo actionInfo) {
    UpdatedFiles files = UpdatedFiles.create();
    MergeChangeCollector collector = new MergeChangeCollector(project, root, currentRev);
    collector.collect(files, exceptions);
    if (!exceptions.isEmpty()) return;

    GuiUtils.invokeLaterIfNeeded(() -> {
      ProjectLevelVcsManagerEx manager = (ProjectLevelVcsManagerEx)ProjectLevelVcsManager.getInstance(project);
      UpdateInfoTree tree = manager.showUpdateProjectInfo(files, actionName, actionInfo, false);
      if (tree != null) {
        tree.setBefore(beforeLabel);
        tree.setAfter(LocalHistory.getInstance().putSystemLabel(project, "After update"));
        ViewUpdateInfoNotification.focusUpdateInfoTree(project, tree);
      }
    }, ModalityState.defaultModalityState());

    Collection<String> unmergedNames = files.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).getFiles();
    if (!unmergedNames.isEmpty()) {
      List<VirtualFile> unmerged = mapNotNull(unmergedNames, name -> LocalFileSystem.getInstance().findFileByPath(name));
      GuiUtils.invokeLaterIfNeeded(() -> AbstractVcsHelper.getInstance(project).showMergeDialog(unmerged, GitVcs.getInstance(project).getMergeProvider()), ModalityState.defaultModalityState());
    }
  }


  public static MergeData loadMergeData(@NotNull Project project,
                                        @NotNull VirtualFile root,
                                        @NotNull VirtualFile file,
                                        boolean isReversed) throws VcsException {
    final FilePath path = VcsUtil.getFilePath(file.getPath());

    GitFileRevision original = new GitFileRevision(project, path, new GitRevisionNumber(":" + ORIGINAL_REVISION_NUM));
    GitFileRevision current = new GitFileRevision(project, path, new GitRevisionNumber(":" + yoursRevision(isReversed)));
    GitFileRevision last = new GitFileRevision(project, path, new GitRevisionNumber(":" + theirsRevision(isReversed)));

    MergeData mergeData = new MergeData();
    mergeData.ORIGINAL = loadOriginalContent(original, file);
    mergeData.CURRENT = loadRevisionCatchingErrors(current);
    mergeData.LAST = loadRevisionCatchingErrors(last);

    // TODO: can be done once for a root
    mergeData.CURRENT_REVISION_NUMBER = findCurrentRevisionNumber(project, root, isReversed);
    mergeData.LAST_REVISION_NUMBER = findLastRevisionNumber(project, root, isReversed);
    mergeData.ORIGINAL_REVISION_NUMBER =
      findOriginalRevisionNumber(project, root, mergeData.CURRENT_REVISION_NUMBER, mergeData.LAST_REVISION_NUMBER);


    Trinity<String, String, String> blobs = getAffectedBlobs(project, root, file, isReversed);

    mergeData.CURRENT_FILE_PATH = getBlobPathInRevision(project, root, file, blobs.getFirst(), mergeData.CURRENT_REVISION_NUMBER);
    mergeData.ORIGINAL_FILE_PATH = getBlobPathInRevision(project, root, file, blobs.getSecond(), mergeData.ORIGINAL_REVISION_NUMBER);
    mergeData.LAST_FILE_PATH = getBlobPathInRevision(project, root, file, blobs.getThird(), mergeData.LAST_REVISION_NUMBER);

    return mergeData;
  }

  @Nullable
  private static GitRevisionNumber findLastRevisionNumber(@NotNull Project project, @NotNull VirtualFile root, boolean isReversed) {
    return isReversed ? resolveHead(project, root) : resolveMergeHead(project, root);
  }

  @Nullable
  private static GitRevisionNumber findCurrentRevisionNumber(@NotNull Project project, @NotNull VirtualFile root, boolean isReversed) {
    return isReversed ? resolveMergeHead(project, root) : resolveHead(project, root);
  }

  @Nullable
  private static GitRevisionNumber findOriginalRevisionNumber(@NotNull Project project,
                                                              @NotNull VirtualFile root,
                                                              @Nullable VcsRevisionNumber currentRevision,
                                                              @Nullable VcsRevisionNumber lastRevision) {
    if (currentRevision == null || lastRevision == null) return null;
    try {
      return GitHistoryUtils.getMergeBase(project, root, currentRevision.asString(), lastRevision.asString());
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

  private static byte[] loadOriginalContent(@NotNull GitFileRevision revision, @NotNull VirtualFile file) {
    try {
      return revision.loadContent();
    }
    catch (Exception ex) {
      /// unable to load original revision, use the current instead
      /// This could happen in case if rebasing.
      try {
        return file.contentsToByteArray();
      }
      catch (IOException e) {
        LOG.error(e);
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
    }
  }

  private static byte[] loadRevisionCatchingErrors(@NotNull GitFileRevision revision) throws VcsException {
    try {
      return revision.loadContent();
    }
    catch (VcsException e) {
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

  @NotNull
  private static Trinity<String, String, String> getAffectedBlobs(@NotNull Project project,
                                                                  @NotNull VirtualFile root,
                                                                  @NotNull VirtualFile file,
                                                                  boolean isReversed) {
    try {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_FILES);
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

        if (source == theirsRevision(isReversed)) {
          lastBlob = blob;
        }
        else if (source == yoursRevision(isReversed)) {
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
  private static FilePath getBlobPathInRevision(@NotNull Project project,
                                                @NotNull VirtualFile root,
                                                @NotNull VirtualFile file,
                                                @Nullable String blob,
                                                @Nullable VcsRevisionNumber revision) {
    if (blob == null || revision == null) return null;

    // fast check if file was not renamed
    FilePath path = doGetBlobPathInRevision(project, root, blob, revision, file);
    if (path != null) return path;

    return doGetBlobPathInRevision(project, root, blob, revision, null);
  }

  @Nullable
  private static FilePath doGetBlobPathInRevision(@NotNull Project project,
                                                  @NotNull final VirtualFile root,
                                                  @NotNull final String blob,
                                                  @NotNull VcsRevisionNumber revision,
                                                  @Nullable VirtualFile file) {
    final FilePath[] result = new FilePath[1];
    final boolean[] pathAmbiguous = new boolean[1];

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());

    if (file != null) {
      h.endOptions();
      h.addRelativeFiles(Collections.singleton(file));
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

  private static int yoursRevision(boolean isReversed) {
    return isReversed ? THEIRS_REVISION_NUM : YOURS_REVISION_NUM;
  }

  private static int theirsRevision(boolean isReversed) {
    return isReversed ? YOURS_REVISION_NUM : THEIRS_REVISION_NUM;
  }
}
