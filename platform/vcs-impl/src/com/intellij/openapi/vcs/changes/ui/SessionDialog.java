// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public class SessionDialog extends DialogWrapper {

  @NonNls public static final String VCS_CONFIGURATION_UI_TITLE = "Vcs.SessionDialog.title";

  private final CommitSession mySession;
  private final List<? extends Change> myChanges;

  private final String myCommitMessage;

  private final JPanel myCenterPanel = new JPanel(new BorderLayout());
  private final JComponent myConfigurationComponent;

  public SessionDialog(String title,
                       Project project,
                       @NotNull CommitSession session,
                       @NotNull List<? extends Change> changes,
                       @Nullable String commitMessage,
                       @Nullable JComponent configurationComponent) {
    super(project, true);
    mySession = session;
    myChanges = changes;
    myCommitMessage = commitMessage;
    myConfigurationComponent =
      configurationComponent == null ? createConfigurationUI(mySession, myChanges, myCommitMessage) : configurationComponent;
    String configurationComponentName =
      myConfigurationComponent != null ? (String)myConfigurationComponent.getClientProperty(VCS_CONFIGURATION_UI_TITLE) : null;
    setTitle(StringUtil.isEmptyOrSpaces(configurationComponentName)
             ? CommitChangeListDialog.trimEllipsis(title) : configurationComponentName);
    init();
    initValidation();
  }

  public SessionDialog(String title,
                       Project project,
                       @NotNull CommitSession session,
                       @NotNull List<? extends Change> changes,
                       @Nullable String commitMessage) {
    this(title, project, session, changes, commitMessage, null);
  }

  @Nullable
  public static JComponent createConfigurationUI(final CommitSession session, final List<? extends Change> changes, final String commitMessage) {
    try {
      return session.getAdditionalConfigurationUI((Collection<Change>)changes, commitMessage);
    }
    catch(AbstractMethodError e) {
      return session.getAdditionalConfigurationUI();
    }
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    myCenterPanel.add(myConfigurationComponent, BorderLayout.CENTER);
    return myCenterPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myConfigurationComponent);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    updateButtons();
    return mySession.validateFields();
  }

  private void updateButtons() {
    setOKActionEnabled(mySession.canExecute((Collection<Change>)myChanges, myCommitMessage));
  }

  @Override
  protected String getHelpId() {
    try {
      return mySession.getHelpId();
    }
    catch (AbstractMethodError e) {
      return null;
    }
  }
}
