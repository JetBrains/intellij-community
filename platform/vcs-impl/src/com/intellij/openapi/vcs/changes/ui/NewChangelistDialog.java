// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public class NewChangelistDialog extends DialogWrapper {

  private NewEditChangelistPanel myPanel ;
  private final Project myProject;

  public NewChangelistDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("changes.dialog.newchangelist.title"));

    createUIComponents();
    myPanel.init(null);
    setSize(JBUI.scale(500), JBUI.scale(230));
    init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE = isNewChangelistActive();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
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
      protected void nameChanged(@NlsContexts.DialogMessage String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage, myPanel);
      }
    };
  }

  @Override
  protected String getHelpId() {
    return "new_changelist_dialog";
  }
}
