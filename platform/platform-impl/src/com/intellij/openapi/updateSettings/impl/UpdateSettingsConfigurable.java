/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author pti
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private UpdatesSettingsPanel myUpdatesSettingsPanel;
  private boolean myCheckNowEnabled = true;

  public void setCheckNowEnabled(boolean enabled) {
    myCheckNowEnabled = enabled;
  }

  @Override
  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    myUpdatesSettingsPanel.myCheckNow.setVisible(myCheckNowEnabled);
    return myUpdatesSettingsPanel.myPanel;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("updates.settings.title");
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "preferences.updates";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  public void apply() throws ConfigurationException {
    UpdateSettings settings = UpdateSettings.getInstance();

    boolean wasEnabled = settings.CHECK_NEEDED;
    settings.CHECK_NEEDED = myUpdatesSettingsPanel.myCheckForUpdates.isSelected();
    if (wasEnabled != settings.CHECK_NEEDED) {
      UpdateCheckerComponent checker = ApplicationManager.getApplication().getComponent(UpdateCheckerComponent.class);
      if (checker != null) {
        if (wasEnabled) {
          checker.cancelChecks();
        }
        else {
          checker.queueNextCheck();
        }
      }
    }

    settings.UPDATE_CHANNEL_TYPE = myUpdatesSettingsPanel.getSelectedChannelType().getCode();
    settings.SECURE_CONNECTION = myUpdatesSettingsPanel.myUseSecureConnection.isSelected();
  }

  @Override
  public void reset() {
    UpdateSettings settings = UpdateSettings.getInstance();
    myUpdatesSettingsPanel.myCheckForUpdates.setSelected(settings.CHECK_NEEDED);
    myUpdatesSettingsPanel.myUseSecureConnection.setSelected(settings.SECURE_CONNECTION);
    myUpdatesSettingsPanel.updateLastCheckedLabel();
    myUpdatesSettingsPanel.setSelectedChannelType(ChannelStatus.fromCode(settings.UPDATE_CHANNEL_TYPE));
  }

  @Override
  public boolean isModified() {
    if (myUpdatesSettingsPanel == null) return false;
    UpdateSettings settings = UpdateSettings.getInstance();
    if (settings.CHECK_NEEDED != myUpdatesSettingsPanel.myCheckForUpdates.isSelected()) return true;
    if (settings.SECURE_CONNECTION != myUpdatesSettingsPanel.myUseSecureConnection.isSelected()) return true;
    Object channel = myUpdatesSettingsPanel.myUpdateChannels.getSelectedItem();
    return channel != null && !channel.equals(ChannelStatus.fromCode(settings.UPDATE_CHANNEL_TYPE));
  }

  @Override
  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  private static class UpdatesSettingsPanel {
    private JPanel myPanel;
    private JButton myCheckNow;
    private JCheckBox myCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;
    private JComboBox myUpdateChannels;
    private JCheckBox myUseSecureConnection;

    public UpdatesSettingsPanel() {
      final ApplicationInfo appInfo = ApplicationInfo.getInstance();
      final String majorVersion = appInfo.getMajorVersion();
      String versionNumber = "";
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        final String minorVersion = appInfo.getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          versionNumber = majorVersion + "." + minorVersion;
        }
        else {
          versionNumber = majorVersion + ".0";
        }
      }
      myVersionNumber.setText(appInfo.getVersionName() + " " + versionNumber);
      myBuildNumber.setText(appInfo.getBuild().asString());

      myCheckNow.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myCheckNow));
          UpdateSettings settings = new UpdateSettings();
          settings.loadState(UpdateSettings.getInstance().getState());
          settings.UPDATE_CHANNEL_TYPE = getSelectedChannelType().getCode();
          settings.SECURE_CONNECTION = myUseSecureConnection.isSelected();
          UpdateChecker.updateAndShowResult(project, settings);
          updateLastCheckedLabel();
        }
      });

      LabelTextReplacingUtil.replaceText(myPanel);

      UpdateSettings settings = UpdateSettings.getInstance();
      //noinspection unchecked
      myUpdateChannels.setModel(new CollectionComboBoxModel(ChannelStatus.all(), ChannelStatus.fromCode(settings.UPDATE_CHANNEL_TYPE)));
    }

    private void updateLastCheckedLabel() {
      long time = UpdateSettings.getInstance().LAST_TIME_CHECKED;
      myLastCheckedDate.setText(time == 0 ? IdeBundle.message("updates.last.check.never") : DateFormatUtil.formatPrettyDateTime(time));
    }

    public ChannelStatus getSelectedChannelType() {
      return (ChannelStatus) myUpdateChannels.getSelectedItem();
    }

    public void setSelectedChannelType(ChannelStatus channelType) {
      myUpdateChannels.setSelectedItem(channelType != null ? channelType : ChannelStatus.RELEASE);
    }
  }
}
