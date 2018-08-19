// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;

import javax.swing.*;

/**
 * @author max
 */
public class NewChangelistDialog extends DialogWrapper {

  private NewEditChangelistPanel myPanel;
  private JPanel myTopPanel;
  private JLabel myErrorLabel;
  private final Project myProject;

  public NewChangelistDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));

    myPanel.init(null);

    init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE = isNewChangelistActive();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  public String getName() {
    return myPanel.getChangeListName();
  }

  public String getDescription() {
    return myPanel.getDescription();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "VCS.EditChangelistDialog";
  }

  public boolean isNewChangelistActive() {
    return myPanel.getMakeActiveCheckBox().isSelected();
  }

  public NewEditChangelistPanel getPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myPanel = new NewEditChangelistPanel(myProject) {
      @Override
      protected void nameChanged(String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        myErrorLabel.setText(errorMessage == null ? " " : errorMessage);
      }
    };
  }

  @Override
  protected String getHelpId() {
    return "new_changelist_dialog";
  }
}
