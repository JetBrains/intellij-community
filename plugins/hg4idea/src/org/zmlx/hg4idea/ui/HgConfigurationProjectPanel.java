// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectConfigurable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;

public class HgConfigurationProjectPanel implements ConfigurableUi<HgProjectConfigurable.HgSettingsHolder> {
  private JPanel myMainPanel;
  private JCheckBox myCheckIncomingOutgoingCbx;
  private JCheckBox myIgnoredWhitespacesInAnnotationsCbx;
  private TextFieldWithBrowseButton myPathSelector;
  private JButton myTestButton;
  private JBCheckBox mySyncControl;

  @NotNull private final Project myProject;

  public HgConfigurationProjectPanel(@NotNull Project project) {
    myProject = project;
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String executable = getCurrentPath();
        HgVersion version;
        try {
          version = HgVersion.identifyVersion(executable);
        }
        catch (Exception exception) {
          Messages.showErrorDialog(myMainPanel, exception.getMessage(), HgVcsMessages.message("hg4idea.run.failed.title"));
          return;
        }
        Messages.showInfoMessage(myMainPanel, String.format("Mercurial version is %s", version.toString()),
                                 HgVcsMessages.message("hg4idea.run.success.title")
        );
      }
    });
    if (!project.isDefault()) {
      final HgRepositoryManager repositoryManager = ServiceManager.getService(project, HgRepositoryManager.class);
      mySyncControl.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControl.setVisible(true);
    }
    mySyncControl.setToolTipText(DvcsBundle.message("sync.setting.description", "Mercurial"));
  }

  private String getCurrentPath() {
    return myPathSelector.getText().trim();
  }

  private void createUIComponents() {
    myPathSelector = new HgSetExecutablePathPanel();
  }

  @Override
  public void reset(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    myCheckIncomingOutgoingCbx.setSelected(settings.getProjectSettings().isCheckIncomingOutgoing());
    myIgnoredWhitespacesInAnnotationsCbx.setSelected(settings.getProjectSettings().isWhitespacesIgnoredInAnnotations());
    myPathSelector.setText(settings.getGlobalSettings().getHgExecutable());
    mySyncControl.setSelected(settings.getProjectSettings().getSyncSetting() == DvcsSyncSettings.Value.SYNC);
  }

  @Override
  public boolean isModified(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    boolean executableModified = !getCurrentPath().equals(settings.getProjectSettings().getHgExecutable());
    return executableModified ||
           myCheckIncomingOutgoingCbx.isSelected() != settings.getProjectSettings().isCheckIncomingOutgoing() ||
           ((settings.getProjectSettings().getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected()) ||
           myIgnoredWhitespacesInAnnotationsCbx.isSelected() != settings.getProjectSettings().isWhitespacesIgnoredInAnnotations();
  }

  @Override
  public void apply(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    settings.getProjectSettings().setCheckIncomingOutgoing(myCheckIncomingOutgoingCbx.isSelected());
    if (myCheckIncomingOutgoingCbx.isSelected()) {
      BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).show();
    }
    else {
      BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).hide();
    }
    settings.getProjectSettings().setIgnoreWhitespacesInAnnotations(myIgnoredWhitespacesInAnnotationsCbx.isSelected());
    settings.getProjectSettings().setHgExecutable(getCurrentPath());
    settings.getProjectSettings().setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    Objects.requireNonNull(HgVcs.getInstance(myProject)).checkVersion();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }
}
