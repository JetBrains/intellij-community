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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:02:55
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;

public class SetDefaultChangeListAction extends AnAction implements DumbAware {
  public SetDefaultChangeListAction() {
    super(VcsBundle.message("changes.action.setdefaultchangelist.text"),
          VcsBundle.message("changes.action.setdefaultchangelist.description"), AllIcons.Actions.Submit1);
  }


  public void update(AnActionEvent e) {
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    final boolean visible =
      lists != null && lists.length == 1 && lists[0] instanceof LocalChangeList && !((LocalChangeList)lists[0]).isDefault();
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP))
      e.getPresentation().setVisible(visible);
    else
      e.getPresentation().setEnabled(visible);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    assert lists != null;
    ChangeListManager.getInstance(project).setDefaultChangeList((LocalChangeList)lists[0]);
  }
}
