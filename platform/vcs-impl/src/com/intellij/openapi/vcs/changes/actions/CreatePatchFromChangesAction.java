// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
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
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.writeAsPatchToClipboard;

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

    createPatch(project, commitMessage, Arrays.asList(changes), mySilentClipboard);
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
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    if (silentClipboard) {
      createIntoClipboard(project, changes);
    }
    else {
      createWithDialog(project, commitMessage, changes);
    }
  }

  private static void createWithDialog(@NotNull Project project, @Nullable String commitMessage, @NotNull List<? extends Change> changes) {
    final CreatePatchCommitExecutor executor = CreatePatchCommitExecutor.getInstance(project);
    CommitSession commitSession = executor.createCommitSession();
    if (commitSession instanceof CommitSessionContextAware) {
      ((CommitSessionContextAware)commitSession).setContext(new CommitContext());
    }
    DialogWrapper sessionDialog = new SessionDialog(executor.getActionText(),
                                                    project,
                                                    commitSession,
                                                    changes,
                                                    commitMessage);
    if (!sessionDialog.showAndGet()) return;

    preloadContent(project, changes);

    commitSession.execute((Collection<Change>)changes, commitMessage);
  }

  private static void createIntoClipboard(@NotNull Project project, @NotNull List<? extends Change> changes) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        String base = PatchWriter.calculateBaseForWritingPatch(project, changes).getPath();
        List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, changes, base, false);
        writeAsPatchToClipboard(project, patches, base, new CommitContext());
        VcsNotifier.getInstance(project).notifySuccess("Patch copied to clipboard");
      }
      catch (IOException | VcsException exception) {
        LOG.error("Can't create patch", exception);
        VcsNotifier.getInstance(project).notifyWeakError("Patch creation failed");
      }
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }

  private static void preloadContent(final Project project, final List<? extends Change> changes) {
    // to avoid multiple progress dialogs, preload content under one progress
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        for (Change change : changes) {
          checkLoadContent(change.getBeforeRevision());
          checkLoadContent(change.getAfterRevision());
        }
      }

      private void checkLoadContent(final ContentRevision revision) {
        ProgressManager.checkCanceled();
        if (revision != null && !(revision instanceof BinaryContentRevision)) {
          try {
            revision.getContent();
          }
          catch (VcsException e1) {
            // ignore at the moment
          }
        }
      }
    }, VcsBundle.message("create.patch.loading.content.progress"), true, project);
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
