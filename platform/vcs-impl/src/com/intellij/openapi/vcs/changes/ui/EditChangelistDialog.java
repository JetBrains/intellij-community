// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class EditChangelistDialog extends DialogWrapper {
  private final NewEditChangelistPanel myPanel;
  private final Project myProject;
  private final @NotNull LocalChangeList myList;

  public EditChangelistDialog(Project project, @NotNull LocalChangeList list) {
    super(project, true);
    myProject = project;
    myList = list;
    myPanel = new NewEditChangelistPanel(project) {
      @Override
      protected void nameChanged(@Nullable @Nls String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage, myPanel);
      }
    };
    myPanel.setChangeListName(list.getName());
    myPanel.setDescription(list.getComment());
    myPanel.init(list);
    myPanel.getMakeActiveCheckBox().setSelected(myList.isDefault());
    myPanel.getMakeActiveCheckBox().setEnabled(!myList.isDefault());
    setTitle(VcsBundle.message("changes.dialog.editchangelist.title"));
    setSize(JBUI.scale(500), JBUI.scale(230));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getContent();
  }

  @Override
  protected void doOKAction() {
    String oldName = myList.getName();
    String oldComment = myList.getComment();

    if (!Objects.equals(oldName, myPanel.getChangeListName()) && ChangeListManager.getInstance(myProject).findChangeList(myPanel.getChangeListName()) != null) {
      Messages.showErrorDialog(myPanel.getContent(),
                               VcsBundle.message("changes.dialog.editchangelist.error.already.exists", myPanel.getChangeListName()),
                               VcsBundle.message("changes.dialog.editchangelist.title"));
      return;
    }

    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);

    final String newDescription = myPanel.getDescription();
    if (!StringUtil.equals(oldComment, newDescription)) {
      clManager.editComment(oldName, newDescription);
      VcsStatisticsCollector.CHANGE_LIST_COMMENT_EDITED.log(myProject, VcsStatisticsCollector.EditChangeListPlace.EDIT_DIALOG);
    }

    final String newName = myPanel.getChangeListName();
    if (!StringUtil.equals(oldName, newName)) {
      clManager.editName(oldName, newName);
      VcsStatisticsCollector.CHANGE_LIST_NAME_EDITED.log(myProject);
    }
    if (!myList.isDefault() && myPanel.getMakeActiveCheckBox().isSelected()) {
      clManager.setDefaultChangeList(newName);
    }
    myPanel.changelistCreatedOrChanged(myList);
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "VCS.EditChangelistDialog";
  }
}
