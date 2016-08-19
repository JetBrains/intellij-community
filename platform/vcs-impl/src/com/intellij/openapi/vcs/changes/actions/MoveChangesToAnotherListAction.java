/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class MoveChangesToAnotherListAction extends AnAction implements DumbAware {

  public MoveChangesToAnotherListAction() {
    super(ActionsBundle.actionText(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST),
          ActionsBundle.actionDescription(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST),
          AllIcons.Actions.MoveToAnotherChangelist);
  }

  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled = isEnabled(e);

    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setEnabledAndVisible(isEnabled);
    }
    else {
      e.getPresentation().setEnabled(isEnabled);
    }
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      return false;
    }

    return !VcsUtil.isEmpty(e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY)) ||
           !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES)) ||
           !ArrayUtil.isEmpty(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
  }

  @NotNull
  private static List<Change> getChangesForSelectedFiles(@NotNull Project project,
                                                         @NotNull VirtualFile[] selectedFiles,
                                                         @NotNull List<VirtualFile> unversionedFiles,
                                                         @NotNull List<VirtualFile> changedFiles) {
    List<Change> changes = new ArrayList<>();
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    for (VirtualFile vFile : selectedFiles) {
      Change change = changeListManager.getChange(vFile);
      if (change == null) {
        FileStatus status = changeListManager.getStatus(vFile);
        if (FileStatus.UNKNOWN.equals(status)) {
          unversionedFiles.add(vFile);
          changedFiles.add(vFile);
        }
        else if (FileStatus.NOT_CHANGED.equals(status) && vFile.isDirectory()) {
          addAllChangesUnderPath(changeListManager, VcsUtil.getFilePath(vFile), changes, changedFiles);
        }
      }
      else {
        FilePath afterPath = ChangesUtil.getAfterPath(change);
        if (afterPath != null && afterPath.isDirectory()) {
          addAllChangesUnderPath(changeListManager, afterPath, changes, changedFiles);
        }
        else {
          changes.add(change);
          changedFiles.add(vFile);
        }
      }
    }
    return changes;
  }

  private static void addAllChangesUnderPath(@NotNull ChangeListManager changeListManager,
                                             @NotNull FilePath file,
                                             @NotNull List<Change> changes,
                                             @NotNull List<VirtualFile> changedFiles) {
    for (Change change : changeListManager.getChangesIn(file)) {
      changes.add(change);

      FilePath path = ChangesUtil.getAfterPath(change);
      if (path != null && path.getVirtualFile() != null) {
        changedFiles.add(path.getVirtualFile());
      }
    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    List<Change> changesList = ContainerUtil.newArrayList();

    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes != null) {
      ContainerUtil.addAll(changesList, changes);
    }

    List<VirtualFile> unversionedFiles = ContainerUtil.newArrayList();
    final List<VirtualFile> changedFiles = ContainerUtil.newArrayList();
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null) {
      changesList.addAll(getChangesForSelectedFiles(project, files, unversionedFiles, changedFiles));
    }

    if (changesList.isEmpty() && unversionedFiles.isEmpty()) {
      VcsBalloonProblemNotifier.showOverChangesView(project, "Nothing is selected that can be moved", MessageType.INFO);
      return;
    }

    if (!askAndMove(project, changesList, unversionedFiles)) return;
    if (!changedFiles.isEmpty()) {
      selectAndShowFile(project, changedFiles.get(0));
    }
  }

  private static void selectAndShowFile(@NotNull final Project project, @NotNull final VirtualFile file) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);

    if (!window.isVisible()) {
      window.activate(new Runnable() {
        public void run() {
          ChangesViewManager.getInstance(project).selectFile(file);
        }
      });
    }
  }

  public static boolean askAndMove(@NotNull Project project,
                                   @NotNull Collection<Change> changes,
                                   @NotNull List<VirtualFile> unversionedFiles) {
    if (changes.isEmpty() && unversionedFiles.isEmpty()) return false;

    LocalChangeList targetList = askTargetList(project, changes);

    if (targetList != null) {
      ChangeListManagerImpl listManager = ChangeListManagerImpl.getInstanceImpl(project);

      listManager.moveChangesTo(targetList, ArrayUtil.toObjectArray(changes, Change.class));
      if (!unversionedFiles.isEmpty()) {
        listManager.addUnversionedFiles(targetList, unversionedFiles);
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static LocalChangeList askTargetList(@NotNull Project project, @NotNull Collection<Change> changes) {
    ChangeListManagerImpl listManager = ChangeListManagerImpl.getInstanceImpl(project);
    List<LocalChangeList> preferredLists = getPreferredLists(listManager.getChangeListsCopy(), changes);
    List<LocalChangeList> listsForChooser =
      preferredLists.isEmpty() ? Collections.singletonList(listManager.getDefaultChangeList()) : preferredLists;
    ChangeListChooser chooser = new ChangeListChooser(project, listsForChooser, guessPreferredList(preferredLists),
                                                      ActionsBundle.message("action.ChangesView.Move.text"), null);
    chooser.show();

    return chooser.getSelectedList();
  }

  @Nullable
  private static ChangeList guessPreferredList(@NotNull List<LocalChangeList> lists) {
    LocalChangeList activeChangeList = ContainerUtil.find(lists, LocalChangeList::isDefault);
    if (activeChangeList != null) return activeChangeList;

    LocalChangeList emptyList = ContainerUtil.find(lists, list -> list.getChanges().isEmpty());

    return ObjectUtils.chooseNotNull(emptyList, ContainerUtil.getFirstItem(lists));
  }

  @NotNull
  private static List<LocalChangeList> getPreferredLists(@NotNull List<LocalChangeList> lists, @NotNull Collection<Change> changes) {
    final Set<Change> changesSet = ContainerUtil.newHashSet(changes);

    return ContainerUtil.findAll(lists, new Condition<LocalChangeList>() {
      @Override
      public boolean value(@NotNull LocalChangeList list) {
        return !ContainerUtil.intersects(changesSet, list.getChanges());
      }
    });
  }
}
