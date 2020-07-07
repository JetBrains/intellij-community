// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor.PatchBuilder;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.calculateBaseForWritingPatch;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.getOnlyItem;

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
  public void defaultActionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (ArrayUtil.isEmpty(changes)) return;
    String commitMessage = extractCommitMessage(e);
    project = chooseNotNull(project, ProjectManager.getInstance().getDefaultProject());

    PatchBuilder patchBuilder;

    ShelvedChangeList shelvedChangeList = getOnlyItem(ShelvedChangesViewManager.getShelvedLists(e.getDataContext()));
    if (shelvedChangeList != null) {
      boolean entireList = getOnlyItem(ShelvedChangesViewManager.getExactlySelectedLists(e.getDataContext())) != null;
      List<String> selectedPaths = entireList ? emptyList() : ShelvedChangesViewManager.getSelectedShelvedChangeNames(e.getDataContext());
      patchBuilder = new CreatePatchCommitExecutor.ShelfPatchBuilder(project, shelvedChangeList, selectedPaths);
    }
    else {
      patchBuilder = new CreatePatchCommitExecutor.DefaultPatchBuilder(project);
    }

    createPatch(project, commitMessage, Arrays.asList(changes), mySilentClipboard, patchBuilder);
  }

  @Nullable
  private static String extractCommitMessage(@NotNull AnActionEvent e) {
    String message = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    if (message != null) return message;

    List<ShelvedChangeList> shelvedChangeLists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    if (!shelvedChangeLists.isEmpty()) {
      return shelvedChangeLists.get(0).DESCRIPTION;
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
    project = chooseNotNull(project, ProjectManager.getInstance().getDefaultProject());
    PatchBuilder patchBuilder = new CreatePatchCommitExecutor.DefaultPatchBuilder(project);
    createPatch(project, commitMessage, changes, silentClipboard, patchBuilder);
  }

  private static void createPatch(@NotNull Project project,
                                  @Nullable String commitMessage,
                                  @NotNull List<? extends Change> changes,
                                  boolean silentClipboard,
                                  @NotNull PatchBuilder patchBuilder) {
    CommitContext commitContext = new CommitContext();
    if (silentClipboard) {
      createIntoClipboard(project, changes, patchBuilder, commitContext);
    }
    else {
      createWithDialog(project, commitMessage, changes, patchBuilder, commitContext);
    }
  }

  private static void createWithDialog(@NotNull Project project,
                                       @Nullable String commitMessage,
                                       @NotNull List<? extends Change> changes, @NotNull PatchBuilder patchBuilder, @NotNull CommitContext commitContext) {
    CommitSession commitSession = CreatePatchCommitExecutor.createCommitSession(project, patchBuilder, commitContext);
    DialogWrapper sessionDialog = new SessionDialog(VcsBundle.message("action.name.create.patch"),
                                                    project,
                                                    commitSession,
                                                    changes,
                                                    commitMessage);
    if (!sessionDialog.showAndGet()) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      //noinspection unchecked
      commitSession.execute((Collection<Change>)changes, commitMessage);
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }

  private static void createIntoClipboard(@NotNull Project project,
                                          @NotNull List<? extends Change> changes,
                                          @NotNull PatchBuilder patchBuilder,
                                          @NotNull CommitContext commitContext) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        Path baseDir = calculateBaseForWritingPatch(project, changes).toNioPath();
        CreatePatchCommitExecutor.writePatchToClipboard(project, baseDir, changes, false, false, patchBuilder, commitContext);
      }
      catch (IOException | VcsException exception) {
        LOG.warn("Can't create patch", exception);
        VcsNotifier.getInstance(project).notifyWeakError(VcsBundle.message("patch.creation.failed"), exception.getMessage());
      }
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }

  @Override
  public void defaultUpdate(@NotNull AnActionEvent e) {
    Boolean haveSelectedChanges = e.getData(VcsDataKeys.HAVE_SELECTED_CHANGES);
    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    List<ShelvedChangeList> shelveChangelists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    int changelistNum = changeLists == null ? 0 : changeLists.length;
    changelistNum += shelveChangelists.size();

    e.getPresentation().setEnabled(!Boolean.FALSE.equals(haveSelectedChanges) &&
                                   changelistNum <= 1 &&
                                   !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES)));
  }
}
