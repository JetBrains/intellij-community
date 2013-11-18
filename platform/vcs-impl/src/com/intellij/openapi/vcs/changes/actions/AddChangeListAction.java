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
 * Time: 21:56:19
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.NewChangelistDialog;

public class AddChangeListAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    NewChangelistDialog dlg = new NewChangelistDialog(project);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String name = dlg.getName();
      if (name.length() == 0) {
        name = getUniqueName(project);
      }

      final LocalChangeList list = ChangeListManager.getInstance(project).addChangeList(name, dlg.getDescription());
      if (dlg.isNewChangelistActive()) {
        ChangeListManager.getInstance(project).setDefaultChangeList(list);
      }
      dlg.getPanel().changelistCreatedOrChanged(list);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getUniqueName(final Project project) {
    int unnamedcount = 0;
    for (ChangeList list : ChangeListManagerImpl.getInstanceImpl(project).getChangeListsCopy()) {
      if (list.getName().startsWith("Unnamed")) {
        unnamedcount++;
      }
    }

    return unnamedcount == 0 ? "Unnamed" : "Unnamed (" + unnamedcount + ")";
  }

  public void update(AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
      e.getPresentation().setVisible(lists != null && lists.length > 0);
    }
  }
}