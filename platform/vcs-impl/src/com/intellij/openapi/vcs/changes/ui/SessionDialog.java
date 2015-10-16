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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SessionDialog extends DialogWrapper {

  @NonNls public static final String VCS_CONFIGURATION_UI_TITLE = "Vcs.SessionDialog.title";

  private final CommitSession mySession;
  private final List<Change> myChanges;

  private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final String myCommitMessage;

  private final JPanel myCenterPanel = new JPanel(new BorderLayout());
  private final JComponent myConfigurationComponent;

  public SessionDialog(String title, Project project,
                       CommitSession session, List<Change> changes,
                       String commitMessage, @Nullable JComponent configurationComponent) {
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
    updateButtons();
  }

  public SessionDialog(String title, Project project,
                       CommitSession session, List<Change> changes,
                       String commitMessage) {
    this(title, project, session, changes, commitMessage, null);
  }

  @Nullable
  public static JComponent createConfigurationUI(final CommitSession session, final List<Change> changes, final String commitMessage) {
    try {
      return session.getAdditionalConfigurationUI(changes, commitMessage);
    }
    catch(AbstractMethodError e) {
      return session.getAdditionalConfigurationUI();
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    myCenterPanel.add(myConfigurationComponent, BorderLayout.CENTER);
    return myCenterPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myConfigurationComponent);
  }

  private void updateButtons() {
    setOKActionEnabled(mySession.canExecute(myChanges, myCommitMessage));
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        updateButtons();
      }
    }, 300, ModalityState.stateForComponent(myCenterPanel));
  }

  protected void dispose() {
    super.dispose();
    myOKButtonUpdateAlarm.cancelAllRequests();
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
