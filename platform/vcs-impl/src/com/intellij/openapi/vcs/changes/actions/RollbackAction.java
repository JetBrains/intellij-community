/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:12:19
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.RollbackUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;

public class RollbackAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final boolean visible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
    e.getPresentation().setEnabledAndVisible(visible);
    if (! visible) return;

    final Change[] leadSelection = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);
    boolean isEnabled = (leadSelection != null && leadSelection.length > 0) ||
                        Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_LOCALLY_DELETED)) ||
                        Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING)) ||
                        Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_SELECTED_CHANGES)) ||
                        hasReversibleFiles(e) ||
                        currentChangelistNotEmpty(project);
    e.getPresentation().setEnabled(isEnabled);
    String operationName = RollbackUtil.getRollbackOperationName(project);
    e.getPresentation().setText(operationName + "...");
    if (isEnabled) {
      e.getPresentation().setDescription(UIUtil.removeMnemonic(operationName) + " selected changes");
    }
  }

  private static boolean hasReversibleFiles(@NotNull AnActionEvent e) {
    ChangeListManager manager = ChangeListManager.getInstance(e.getRequiredData(CommonDataKeys.PROJECT));
    Set<VirtualFile> modifiedWithoutEditing = ContainerUtil.newHashSet(manager.getModifiedWithoutEditing());

    return VcsUtil.notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)).anyMatch(
      file -> manager.haveChangesUnder(file) != ThreeState.NO || manager.isFileAffected(file) || modifiedWithoutEditing.contains(file));
  }

  private static boolean currentChangelistNotEmpty(Project project) {
    ChangeListManager clManager = ChangeListManager.getInstance(project);
    ChangeList list = clManager.getDefaultChangeList();
    return list != null && !list.getChanges().isEmpty();
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final String title = ActionPlaces.CHANGES_VIEW_TOOLBAR.equals(e.getPlace())
                         ? null
                         : "Can not " + RollbackUtil.getRollbackOperationName(project) + " now";
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(title)) return;
    FileDocumentManager.getInstance().saveAllDocuments();

    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    boolean hasChanges = false;
    if (missingFiles != null && !missingFiles.isEmpty()) {
      hasChanges = true;
      new RollbackDeletionAction().actionPerformed(e);
    }

    List<Change> changes = getChanges(project, e);

    final LinkedHashSet<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e, project);
    if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
      hasChanges = true;
      rollbackModifiedWithoutEditing(project, modifiedWithoutEditing);
    }

    if (modifiedWithoutEditing != null) {
      changes = ContainerUtil.filter(changes, new Condition<Change>() {
        @Override
        public boolean value(Change change) {
          return !modifiedWithoutEditing.contains(change.getVirtualFile());
        }
      });
    }

    if (!changes.isEmpty()) {
      RollbackChangesDialog.rollbackChanges(project, changes);
    }
    else if (!hasChanges) {
      LocalChangeList currentChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
      RollbackChangesDialog.rollbackChanges(project, currentChangeList);
    }
  }

  @NotNull
  private static List<Change> getChanges(final Project project, final AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) {
      final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (files != null) {
        final ChangeListManager clManager = ChangeListManager.getInstance(project);
        final List<Change> changesList = new ArrayList<>();
        for (VirtualFile vf : files) {
          changesList.addAll(clManager.getChangesIn(vf));
        }
        if (!changesList.isEmpty()) {
          changes = changesList.toArray(new Change[changesList.size()]);
        }
      }
    }
    if (changes != null && changes.length > 0) {
      return ContainerUtil.newArrayList(changes);
    }
    return Collections.emptyList();
  }

  @Nullable
  private static LinkedHashSet<VirtualFile> getModifiedWithoutEditing(final AnActionEvent e, Project project) {
    final List<VirtualFile> modifiedWithoutEditing = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (modifiedWithoutEditing != null && modifiedWithoutEditing.size() > 0) {
      return new LinkedHashSet<>(modifiedWithoutEditing);
    }

    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      LinkedHashSet<VirtualFile> result = new LinkedHashSet<>(Arrays.asList(virtualFiles));
      result.retainAll(ChangeListManager.getInstance(project).getModifiedWithoutEditing());
      return result;
    }

    return null;
  }

  private static void rollbackModifiedWithoutEditing(final Project project, final LinkedHashSet<VirtualFile> modifiedWithoutEditing) {
    final String operationName = StringUtil.decapitalize(UIUtil.removeMnemonic(RollbackUtil.getRollbackOperationName(project)));
    String message = (modifiedWithoutEditing.size() == 1)
                     ? VcsBundle.message("rollback.modified.without.editing.confirm.single",
                                         operationName, modifiedWithoutEditing.iterator().next().getPresentableUrl())
                     : VcsBundle.message("rollback.modified.without.editing.confirm.multiple",
                                         operationName, modifiedWithoutEditing.size());
    int rc = showYesNoDialog(project, message, VcsBundle.message("changes.action.rollback.title", operationName), getQuestionIcon());
    if (rc != Messages.YES) {
      return;
    }
    final List<VcsException> exceptions = new ArrayList<>();

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Runnable action = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = progressManager.getProgressIndicator();
        try {
          ChangesUtil.processVirtualFilesByVcs(project, modifiedWithoutEditing, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
            public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
              final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
              if (rollbackEnvironment != null) {
                if (indicator != null) {
                  indicator.setText(vcs.getDisplayName() +
                                    ": performing " + UIUtil.removeMnemonic(rollbackEnvironment.getRollbackOperationName()).toLowerCase() + "...");
                  indicator.setIndeterminate(false);
                }
                rollbackEnvironment
                  .rollbackModifiedWithoutCheckout(items, exceptions, new RollbackProgressModifier(items.size(), indicator));
                if (indicator != null) {
                  indicator.setText2("");
                }
              }
            }
          });
        }
        catch (ProcessCanceledException e) {
          // for files refresh  
        }
        if (!exceptions.isEmpty()) {
          AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("rollback.modified.without.checkout.error.tab",
                                                                                          operationName));
        }

        VfsUtil.markDirty(true, false, VfsUtilCore.toVirtualFileArray(modifiedWithoutEditing));

        VirtualFileManager.getInstance().asyncRefresh(new Runnable() {
          public void run() {
            for (VirtualFile virtualFile : modifiedWithoutEditing) {
              VcsDirtyScopeManager.getInstance(project).fileDirty(virtualFile);
            }
          }
        });
      }
    };
    progressManager.runProcessWithProgressSynchronously(action, operationName, true, project);
  }
}
