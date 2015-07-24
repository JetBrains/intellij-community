/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * Date: 23.11.2006
 * Time: 17:20:10
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UnshelveChangesAction extends DumbAwareAction {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.UnshelveChangesAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    List<ShelvedChange> changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY);
    List<ShelvedBinaryFile> binaryFiles = e.getData(ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY);
    if (changes != null && binaryFiles != null && changes.size() == 0 && binaryFiles.size() == 0) {
      changes = null;
      binaryFiles = null;
    }
    LOG.assertTrue(changeLists != null);

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final List<LocalChangeList> allChangeLists = changeListManager.getChangeListsCopy();
    String defaultName = changeLists[0].DESCRIPTION;
    LocalChangeList list = null;
    if (changeLists.length == 1) {
      final LocalChangeList sameNamedList = changeListManager.findChangeList(defaultName);
      if (sameNamedList != null) {
        list = sameNamedList;
      }
    }
    if (list == null) {
      list = changeListManager.getDefaultChangeList();
    }
    final ChangeListChooser chooser = new ChangeListChooser(project, allChangeLists, list,
                                                            VcsBundle.message("unshelve.changelist.chooser.title"), defaultName);
    if (!chooser.showAndGet()) {
      return;
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    final List<ShelvedBinaryFile> finalBinaryFiles = binaryFiles;
    final List<ShelvedChange> finalChanges = changes;
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Unshelve changes", true, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (ShelvedChangeList changeList : changeLists) {
          ShelveChangesManager.getInstance(project)
            .unshelveChangeList(changeList, finalChanges, finalBinaryFiles, chooser.getSelectedList(), true);
        }
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null);
  }
}
