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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class EditChangelistDialog extends DialogWrapper {
  private final EditChangelistPanel myPanel;
  private final Project myProject;
  private final LocalChangeList myList;

  public EditChangelistDialog(Project project, @NotNull LocalChangeList list) {
    super(project, true);
    myProject = project;
    myList = list;
    myPanel = new EditChangelistPanel(((LocalChangeListImpl) list).getEditHandler()) {
      @Override
      protected void nameChanged(String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage);
      }
    };
    myPanel.setName(list.getName());
    myPanel.setDescription(list.getComment());
    myPanel.init(project, list);
    myPanel.getMakeActiveCheckBox().setSelected(myList.isDefault());
    myPanel.getMakeActiveCheckBox().setEnabled(!myList.isDefault());
    setTitle(VcsBundle.message("changes.dialog.editchangelist.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel.getContent();
  }

  protected void doOKAction() {
    String oldName = myList.getName();
    String oldComment = myList.getComment();

    if (!Comparing.equal(oldName, myPanel.getName()) && ChangeListManager.getInstance(myProject).findChangeList(myPanel.getName()) != null) {
      Messages.showErrorDialog(myPanel.getContent(),
                               VcsBundle.message("changes.dialog.editchangelist.error.already.exists", myPanel.getName()),
                               VcsBundle.message("changes.dialog.editchangelist.title"));
      return;
    }

    if (!Comparing.equal(oldName, myPanel.getName(), true) || !Comparing.equal(oldComment, myPanel.getDescription(), true)) {
      final ChangeListManager clManager = ChangeListManager.getInstance(myProject);

      final String newName = myPanel.getName();
      if (! myList.getName().equals(newName)) {
        clManager.editName(myList.getName(), newName);
      }
      final String newDescription = myPanel.getDescription();
      if (! myList.getComment().equals(newDescription)) {
        clManager.editComment(myList.getName(), newDescription);
      }
    }
    if (!myList.isDefault() && myPanel.getMakeActiveCheckBox().isSelected()) {
      ChangeListManager.getInstance(myProject).setDefaultChangeList(myList);  
    }
    myPanel.changelistCreatedOrChanged(myList);
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.EditChangelistDialog";
  }
}
