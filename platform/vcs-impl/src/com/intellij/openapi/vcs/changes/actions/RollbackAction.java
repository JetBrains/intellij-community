/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.RollbackUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;
import static java.util.Arrays.asList;

public class RollbackAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final boolean visible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
    e.getPresentation().setVisible(visible);
    if (! visible) return;

    final Change[] leadSelection = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);
    boolean isEnabled = (leadSelection != null && leadSelection.length > 0) ||
                              Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_LOCALLY_DELETED)) ||
                              Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING)) ||
                              Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_SELECTED_CHANGES)) ||
                              hasReversibleFiles(e, project);
    e.getPresentation().setEnabled(isEnabled);
    String operationName = RollbackUtil.getRollbackOperationName(project);
    e.getPresentation().setText(operationName + "...");
    if (isEnabled) {
      e.getPresentation().setDescription(UIUtil.removeMnemonic(operationName) + " selected changes");
    }
  }

  private static boolean hasReversibleFiles(AnActionEvent e, Project project) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null) {
      ChangeListManager clManager = ChangeListManager.getInstance(project);
      for (VirtualFile file : files) {
        if (!clManager.getChangesIn(file).isEmpty()) {
          return true;
        }
      }
    }
    return false;
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

    LinkedHashSet<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e, project);
    if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
      hasChanges = true;
      rollbackModifiedWithoutEditing(project, modifiedWithoutEditing);
    }

    List<Change> changes = getChanges(project, e);
    if (changes != null) {
      if (modifiedWithoutEditing != null) {
        for (Iterator<Change> iterator = changes.iterator(); iterator.hasNext(); ) {
          Change next = iterator.next();
          if (modifiedWithoutEditing.contains(next.getVirtualFile())) {
            iterator.remove();
          }
        }
      }
      if (!changes.isEmpty() || !hasChanges) {
        RollbackChangesDialog.rollbackChanges(project, changes);
      }
    }
  }

  private static class ChangesCheckHelper {
    private Change[] myChanges;
    private final boolean myChangesSet;

    public ChangesCheckHelper(final Project project, final AnActionEvent e) {
      Change[] changes = e.getData(VcsDataKeys.CHANGES);
      if (changes == null) {
        final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null) {
          final ChangeListManager clManager = ChangeListManager.getInstance(project);
          final List<Change> changesList = new LinkedList<Change>();
          for (VirtualFile vf : files) {
            changesList.addAll(clManager.getChangesIn(vf));
          }
          if (! changesList.isEmpty()) {
            changes = changesList.toArray(new Change[changesList.size()]);
          }
        }
      }
      myChangesSet = changes != null && changes.length > 0;
      if (myChangesSet) {
        if (ChangesUtil.allChangesInOneListOrWholeListsSelected(project, changes)) {
          myChanges = changes;
        }
      }
    }

    public boolean isChangesSet() {
      return myChangesSet;
    }

    public Change[] getChanges() {
      return myChanges;
    }
  }

  @Nullable
  private static List<Change> getChanges(final Project project, final AnActionEvent e) {
    final ChangesCheckHelper helper = new ChangesCheckHelper(project, e);
    if (helper.isChangesSet() && helper.getChanges() != null) return ContainerUtil.newArrayList(helper.getChanges());

    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      List<Change> result = new ArrayList<Change>();
      for(VirtualFile file: virtualFiles) {
        result.addAll(ChangeListManager.getInstance(project).getChangesIn(file));
      }
      return result;
    }
    return null;
  }

  @Nullable
  private static LinkedHashSet<VirtualFile> getModifiedWithoutEditing(final AnActionEvent e, Project project) {
    final List<VirtualFile> modifiedWithoutEditing = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (modifiedWithoutEditing != null && modifiedWithoutEditing.size() > 0) {
      return new LinkedHashSet<VirtualFile>(modifiedWithoutEditing);
    }

    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      LinkedHashSet<VirtualFile> result = new LinkedHashSet<VirtualFile>(asList(virtualFiles));
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
    if (rc != 0) {
      return;
    }
    final List<VcsException> exceptions = new ArrayList<VcsException>();

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
