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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;

import java.util.Arrays;

/**
 * @author yole
*/
public class RollbackDialogAction extends AnAction implements DumbAware {
  public RollbackDialogAction() {
    super(VcsBundle.message("changes.action.rollback.text"), VcsBundle.message("changes.action.rollback.description"),
          IconLoader.getIcon("/actions/rollback.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final ChangesBrowser browser = e.getData(ChangesBrowser.DATA_KEY);
    if (browser != null) {
      browser.setDataIsDirty(true);
    }
    RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes), true, new Runnable() {
      public void run() {
        if (browser != null) {
          browser.rebuildList();
          browser.setDataIsDirty(false);
        }
      }
    });
  }

  public void update(AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(changes != null);
  }
}
