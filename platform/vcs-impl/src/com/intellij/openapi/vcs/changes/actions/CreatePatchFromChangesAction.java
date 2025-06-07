// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor.PatchBuilder;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.PATCH_CREATION_FAILED;

public abstract class CreatePatchFromChangesAction extends ExtendableAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(CreatePatchFromChangesAction.class);
  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME_DIALOG =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction.Dialog.ExtensionProvider");
  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME_CLIPBOARD =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction.Clipboard.ExtensionProvider");

  private final boolean mySilentClipboard;

  private CreatePatchFromChangesAction(boolean silentClipboard) {
    super(silentClipboard ? EP_NAME_CLIPBOARD : EP_NAME_DIALOG);
    mySilentClipboard = silentClipboard;
  }

  public static class Dialog extends CreatePatchFromChangesAction {
    public Dialog() {
      super(false);
    }
  }

  public static class Clipboard extends CreatePatchFromChangesAction {
    public Clipboard() {
      super(true);
    }
  }

  @Override
  public void defaultUpdate(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    JBIterable<FilePath> unversioned = JBIterable.from(e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY));
    if (ArrayUtil.isEmpty(changes) && unversioned.isEmpty()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    List<ShelvedChangeList> shelveChangelists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    int changelistNum = changeLists == null ? 0 : changeLists.length;
    changelistNum += shelveChangelists.size();
    if (changelistNum > 1) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabled(true);
  }

  @Override
  public void defaultActionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    JBIterable<FilePath> unversioned = JBIterable.from(e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY));

    List<Change> allChanges = new ArrayList<>();
    if (changes != null) ContainerUtil.addAll(allChanges, changes);
    ContainerUtil.addAll(allChanges, unversioned.map(path -> new Change(null, new CurrentContentRevision(path))));
    if (allChanges.isEmpty()) return;

    String commitMessage = extractCommitMessage(e);
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;

    PatchBuilder patchBuilder;

    ShelvedChangeList shelvedChangeList = ContainerUtil.getOnlyItem(ShelvedChangesViewManager.getShelvedLists(e.getDataContext()));
    if (shelvedChangeList != null) {
      boolean entireList = ContainerUtil.getOnlyItem(ShelvedChangesViewManager.getExactlySelectedLists(e.getDataContext())) != null;
      List<String> selectedPaths = entireList ? ContainerUtil.emptyList()
                                              : ShelvedChangesViewManager.getSelectedShelvedChangeNames(e.getDataContext());
      patchBuilder = new CreatePatchCommitExecutor.ShelfPatchBuilder(project, shelvedChangeList, selectedPaths);
    }
    else {
      patchBuilder = new CreatePatchCommitExecutor.DefaultPatchBuilder(project);
    }

    createPatch(project, commitMessage, allChanges, mySilentClipboard, patchBuilder);
  }

  private static @Nullable String extractCommitMessage(@NotNull AnActionEvent e) {
    String message = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    if (message != null) return message;

    List<ShelvedChangeList> shelvedChangeLists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    if (!shelvedChangeLists.isEmpty()) {
      return shelvedChangeLists.get(0).getDescription();
    }

    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0) {
      return changeLists[0].getComment();
    }

    return null;
  }

  public static void createPatch(@Nullable Project project,
                                 @Nullable String commitMessage,
                                 @NotNull List<? extends Change> changes) {
    createPatch(project, commitMessage, changes, false);
  }

  public static void createPatch(@Nullable Project project,
                                 @Nullable String commitMessage,
                                 @NotNull List<? extends Change> changes,
                                 boolean silentClipboard) {
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    PatchBuilder patchBuilder = new CreatePatchCommitExecutor.DefaultPatchBuilder(project);
    createPatch(project, commitMessage, changes, silentClipboard, patchBuilder);
  }

  public static void createPatch(@NotNull Project project,
                                  @Nullable String commitMessage,
                                  @NotNull List<? extends Change> changes,
                                  boolean silentClipboard,
                                  @NotNull PatchBuilder patchBuilder) {
    CommitContext commitContext = new CommitContext();
    if (silentClipboard) {
      createIntoClipboard(project, changes, commitMessage, patchBuilder, commitContext);
    }
    else {
      createWithDialog(project, commitMessage, changes, patchBuilder, commitContext);
    }
  }

  private static void createWithDialog(@NotNull Project project,
                                       @Nullable String commitMessage,
                                       @NotNull List<? extends Change> changes,
                                       @NotNull PatchBuilder patchBuilder,
                                       @NotNull CommitContext commitContext) {
    CommitSession commitSession = CreatePatchCommitExecutor.createCommitSession(project, patchBuilder, commitContext);

    String title = VcsBundle.message("action.name.create.patch");
    if (!SessionDialog.configureCommitSession(project, title, commitSession, changes, commitMessage)) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      commitSession.execute(changes, commitMessage);
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }

  private static void createIntoClipboard(@NotNull Project project,
                                          @NotNull List<? extends Change> changes,
                                          @Nullable String commitMessage,
                                          @NotNull PatchBuilder patchBuilder,
                                          @NotNull CommitContext commitContext) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        Path baseDir = PatchWriter.calculateBaseDirForWritingPatch(project, changes);
        CreatePatchCommitExecutor.writePatchToClipboard(project, baseDir, changes, commitMessage, false, false,
                                                        patchBuilder, commitContext);
      }
      catch (IOException | VcsException exception) {
        LOG.warn("Can't create patch", exception);
        VcsNotifier.getInstance(project).notifyWeakError(PATCH_CREATION_FAILED,
                                                         VcsBundle.message("patch.creation.failed"),
                                                         exception.getMessage());
      }
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }
}
