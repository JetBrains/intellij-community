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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.VcsExecutablePathSelector;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.*;
import java.util.Objects;

public class HgConfigurationProjectPanel implements ConfigurableUi<HgProjectConfigurable.HgSettingsHolder>, Disposable {
  private final BorderLayoutPanel myMainPanel;
  private final VcsExecutablePathSelector myExecutablePathSelector;
  private final JBCheckBox myCheckIncomingOutgoingCbx;
  private final JBCheckBox myIgnoredWhitespacesInAnnotationsCbx;
  private final JBCheckBox mySyncControl;

  @NotNull private final Project myProject;

  public HgConfigurationProjectPanel(@NotNull Project project) {
    myProject = project;

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    myExecutablePathSelector = new VcsExecutablePathSelector("Mercurial", this, this::testExecutable);
    panel.add(myExecutablePathSelector.getMainPanel());

    myCheckIncomingOutgoingCbx = new JBCheckBox(HgBundle.message("hg4idea.configuration.check.incoming.outgoing"));
    panel.add(UI.PanelFactory.panel(myCheckIncomingOutgoingCbx).createPanel());

    myIgnoredWhitespacesInAnnotationsCbx = new JBCheckBox(HgBundle.message("hg4idea.configuration.ignore.whitespace.in.annotate"));
    panel.add(UI.PanelFactory.panel(myIgnoredWhitespacesInAnnotationsCbx).createPanel());

    mySyncControl = new JBCheckBox(DvcsBundle.getString("sync.setting"));
    JPanel mySyncControlPanel = Objects.requireNonNull(UI.PanelFactory.panel(mySyncControl)
                                                         .withTooltip(DvcsBundle.message("sync.setting.description", "Mercurial"))
                                                         .createPanel());
    if (!project.isDefault()) {
      final HgRepositoryManager repositoryManager = ServiceManager.getService(project, HgRepositoryManager.class);
      mySyncControlPanel.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControlPanel.setVisible(true);
    }
    panel.add(mySyncControlPanel);

    myMainPanel = JBUI.Panels.simplePanel();
    myMainPanel.addToTop(panel);
  }

  @Override
  public void dispose() {
  }

  private void testExecutable(@NotNull String executable) {
    new Task.Modal(myProject, HgBundle.message("hg4idea.configuration.identifying.version"), true) {
      HgVersion version;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          version = HgVersion.identifyVersion(executable);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void onSuccess() {
        Messages.showInfoMessage(myMainPanel, HgBundle.message("hg4idea.configuration.version", version.toString()),
                                 HgBundle.message("hg4idea.run.success.title"));
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        Messages.showErrorDialog(myMainPanel, error.getCause().getMessage(), HgBundle.message("hg4idea.run.failed.title"));
      }
    }.queue();
  }

  @Override
  public void reset(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    HgProjectSettings projectSettings = settings.getProjectSettings();

    myExecutablePathSelector.reset(settings.getGlobalSettings().getHgExecutable(),
                                   projectSettings.isHgExecutableOverridden(),
                                   projectSettings.getHgExecutable(),
                                   HgExecutableManager.getInstance().getDefaultExecutable());
    myCheckIncomingOutgoingCbx.setSelected(projectSettings.isCheckIncomingOutgoing());
    myIgnoredWhitespacesInAnnotationsCbx.setSelected(projectSettings.isWhitespacesIgnoredInAnnotations());
    mySyncControl.setSelected(projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC);
  }

  @Override
  public boolean isModified(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    HgProjectSettings projectSettings = settings.getProjectSettings();

    return myExecutablePathSelector.isModified(settings.getGlobalSettings().getHgExecutable(),
                                               projectSettings.isHgExecutableOverridden(),
                                               projectSettings.getHgExecutable()) ||
           myCheckIncomingOutgoingCbx.isSelected() != projectSettings.isCheckIncomingOutgoing() ||
           ((projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected()) ||
           myIgnoredWhitespacesInAnnotationsCbx.isSelected() != projectSettings.isWhitespacesIgnoredInAnnotations();
  }

  @Override
  public void apply(@NotNull HgProjectConfigurable.HgSettingsHolder settings) {
    HgGlobalSettings globalSettings = settings.getGlobalSettings();
    HgProjectSettings projectSettings = settings.getProjectSettings();

    boolean executablePathOverridden = myExecutablePathSelector.isOverridden();
    projectSettings.setHgExecutableOverridden(executablePathOverridden);
    if (executablePathOverridden) {
      projectSettings.setHgExecutable(myExecutablePathSelector.getCurrentPath());
    }
    else {
      globalSettings.setHgExecutable(myExecutablePathSelector.getCurrentPath());
      projectSettings.setHgExecutable(null);
    }
    projectSettings.setCheckIncomingOutgoing(myCheckIncomingOutgoingCbx.isSelected());
    projectSettings.setIgnoreWhitespacesInAnnotations(myIgnoredWhitespacesInAnnotationsCbx.isSelected());
    projectSettings.setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    Objects.requireNonNull(HgVcs.getInstance(myProject)).checkVersion();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }
}
