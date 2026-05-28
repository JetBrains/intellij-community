// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.conflicts;

import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer.DiffEditorTitleCustomizerList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.merge.GitMergeUtil;
import git4idea.repo.GitConflict;
import git4idea.repo.GitConflict.ConflictSide;
import git4idea.repo.GitConflict.Status;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.status.GitStagingAreaHolder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class GitMergeHandler {
  private static final Logger LOG = Logger.getInstance(GitMergeHandler.class);

  private final @NotNull Project myProject;
  private final @NotNull MergeDialogCustomizer myDialogCustomizer;

  public GitMergeHandler(@NotNull Project project, @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    myProject = project;
    myDialogCustomizer = mergeDialogCustomizer;
  }

  public @Nls @NotNull String loadMergeDescription() {
    return myDialogCustomizer.getMultipleFileMergeDescription(emptyList());
  }

  public boolean canResolveConflict(@NotNull GitConflict conflict) {
    VirtualFile file = conflict.getFilePath().getVirtualFile();
    if (file == null) return false;
    if (file.isDirectory()) return false; // Can't handle conflicts in submodules
    return conflict.getStatus(ConflictSide.OURS) != Status.DELETED ||
           conflict.getStatus(ConflictSide.THEIRS) != Status.DELETED;
  }

  public @NotNull Resolver resolveConflict(@NotNull GitConflict conflict, @NotNull VirtualFile file, boolean isReversed) throws VcsException {
    VirtualFile root = conflict.getRoot();
    FilePath path = conflict.getFilePath();

    MergeData mergeData = GitMergeUtil.loadMergeData(myProject, root, path, isReversed);

    String windowTitle = myDialogCustomizer.getMergeWindowTitle(file);
    String leftTitle = myDialogCustomizer.getLeftPanelTitle(file);
    String centerTitle = myDialogCustomizer.getCenterPanelTitle(file);
    String rightTitle = myDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER);

    DiffEditorTitleCustomizerList titleCustomizerList = myDialogCustomizer.getTitleCustomizerList(path);

    return new Resolver(myProject, conflict, isReversed, file, mergeData,
                        windowTitle, Arrays.asList(leftTitle, centerTitle, rightTitle), titleCustomizerList);
  }

  public void acceptOneVersion(@NotNull Collection<? extends GitConflict> conflicts,
                               @NotNull Collection<? extends VirtualFile> reversedRoots,
                               boolean takeTheirs) throws VcsException {
    try {
      MultiMap<VirtualFile, GitConflict> byRoot = groupConflictsByRoot(conflicts);

      for (VirtualFile root : byRoot.keySet()) {
        Collection<GitConflict> rootConflicts = byRoot.get(root);
        boolean isReversed = reversedRoots.contains(root);
        ConflictSide side = isReversed == takeTheirs ? ConflictSide.OURS : ConflictSide.THEIRS;

        GitMergeUtil.acceptOneVersion(myProject, root, rootConflicts, side);
        GitMergeUtil.markConflictResolved(myProject, root, rootConflicts, side);
      }
    }
    finally {
      List<FilePath> filePaths = ContainerUtil.map(conflicts, GitConflict::getFilePath);
      VcsDirtyScopeManager.getInstance(myProject).filePathsDirty(filePaths, null);

      List<VirtualFile> virtualFiles = ContainerUtil.mapNotNull(filePaths, GitMergeHandler::getExistingFileOrParent);
      VfsUtil.markDirtyAndRefresh(true, false, true, VfsUtilCore.toVirtualFileArray(virtualFiles));
    }
  }

  private static @Nullable VirtualFile getExistingFileOrParent(@NotNull FilePath filePath) {
    VirtualFile file = filePath.getVirtualFile();
    if (file != null) return file;
    return filePath.getVirtualFileParent();
  }

  public static @NotNull MultiMap<VirtualFile, GitConflict> groupConflictsByRoot(@NotNull Collection<? extends GitConflict> conflicts) {
    MultiMap<VirtualFile, GitConflict> byRoot = MultiMap.create();
    for (GitConflict conflict : conflicts) {
      byRoot.putValue(conflict.getRoot(), conflict);
    }
    return byRoot;
  }

  public static final class Resolver {
    private final @NotNull Project myProject;
    private final @NotNull GitConflict myConflict;
    private final boolean myIsReversed;
    private final @NotNull MergeData myMergeData;
    private final @NotNull VirtualFile myFile;

    private final @NotNull String myWindowTitle;
    private final @NotNull List<String> myContentTitles;
    private final @NotNull DiffEditorTitleCustomizerList myTitleCustomizerList;

    private volatile boolean myIsValid = true;

    private Resolver(@NotNull Project project,
                     @NotNull GitConflict conflict,
                     boolean isReversed,
                     @NotNull VirtualFile file,
                     @NotNull MergeData mergeData,
                     @NotNull String windowTitle,
                     @NotNull List<String> contentTitles,
                     @NotNull DiffEditorTitleCustomizerList titleCustomizerList) {
      myProject = project;
      myConflict = conflict;
      myIsReversed = isReversed;
      myMergeData = mergeData;
      myFile = file;
      myWindowTitle = windowTitle;
      myContentTitles = contentTitles;
      myTitleCustomizerList = titleCustomizerList;
    }

    public @NotNull Project getProject() {
      return myProject;
    }

    public @NotNull VirtualFile getVirtualFile() {
      return myFile;
    }

    public @NotNull MergeData getMergeData() {
      return myMergeData;
    }

    public void onConflictResolved(@NotNull MergeResult result) {
      if (result != MergeResult.CANCEL) {
        try {
          GitMergeUtil.markConflictResolved(myProject, myConflict.getRoot(), singletonList(myConflict), getResolutionSide(result));
        }
        catch (VcsException e) {
          LOG.error(String.format("Unexpected exception during the git operation: file - %s)", myConflict.getFilePath()), e);
        }
      }
      VcsDirtyScopeManager.getInstance(myProject).fileDirty(myFile);
    }

    private @Nullable ConflictSide getResolutionSide(@NotNull MergeResult result) {
      return switch (result) {
        case LEFT -> !myIsReversed ? ConflictSide.OURS : ConflictSide.THEIRS;
        case RIGHT -> myIsReversed ? ConflictSide.OURS : ConflictSide.THEIRS;
        default -> null;
      };
    }

    public @NotNull String getWindowTitle() {
      return myWindowTitle;
    }

    public @NotNull List<String> getContentTitles() {
      return myContentTitles;
    }

    public @NotNull DiffEditorTitleCustomizerList getTitleCustomizerList() {
      return myTitleCustomizerList;
    }

    public boolean checkIsValid() {
      if (myIsValid) {
        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForRootQuick(myConflict.getRoot());
        if (repository == null) return true;
        myIsValid = repository.getStagingAreaHolder().findConflict(myConflict.getFilePath()) != null;
      }
      return myIsValid;
    }

    public void addListener(@NotNull MergeCallback.Listener listener, @NotNull Disposable disposable) {
      myProject.getMessageBus().connect(disposable).subscribe(GitStagingAreaHolder.TOPIC, (repo) -> {
        if (myIsValid && myConflict.getRoot().equals(repo.getRoot())) {
          if (!checkIsValid()) {
            listener.fireConflictInvalid();
          }
        }
      });
    }
  }
}
