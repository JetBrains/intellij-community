/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * @author pti
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final UpdateSettings mySettings;
  private final boolean myCheckNowEnabled;
  private UpdatesSettingsPanel myPanel;

  @SuppressWarnings("unused")
  public UpdateSettingsConfigurable() {
    this(true);
  }

  public UpdateSettingsConfigurable(boolean checkNowEnabled) {
    mySettings = UpdateSettings.getInstance();
    myCheckNowEnabled = checkNowEnabled;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new UpdatesSettingsPanel(myCheckNowEnabled);
    return myPanel.myPanel;
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

  @Override
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
    if (myPanel.myUseSecureConnection.isSelected() && !NetUtils.isSniEnabled()) {
      boolean tooOld = !SystemInfo.isJavaVersionAtLeast("1.7");
      String message = IdeBundle.message(tooOld ? "update.sni.not.available.error" : "update.sni.disabled.error");
      throw new ConfigurationException(message);
    }

    boolean wasEnabled = mySettings.isCheckNeeded();
    mySettings.setCheckNeeded(myPanel.myCheckForUpdates.isSelected());
    if (wasEnabled != mySettings.isCheckNeeded()) {
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

    mySettings.setSelectedChannelStatus(myPanel.getSelectedChannelType());
    mySettings.setSecureConnection(myPanel.myUseSecureConnection.isSelected());
  }

  @Override
  public void reset() {
    myPanel.myCheckForUpdates.setSelected(mySettings.isCheckNeeded());
    myPanel.myUseSecureConnection.setSelected(mySettings.isSecureConnection());
    myPanel.updateLastCheckedLabel();
    myPanel.setSelectedChannelType(mySettings.getSelectedChannelStatus());
  }

  @Override
  public boolean isModified() {
    if (myPanel == null) {
      return false;
    }

    if (mySettings.isCheckNeeded() != myPanel.myCheckForUpdates.isSelected() ||
        mySettings.isSecureConnection() != myPanel.myUseSecureConnection.isSelected()) {
      return true;
    }

    Object channel = myPanel.myUpdateChannels.getSelectedItem();
    return channel != null && !channel.equals(mySettings.getSelectedChannelStatus());
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private static class UpdatesSettingsPanel {
    private final UpdateSettings mySettings;
    private JPanel myPanel;
    private JCheckBox myCheckForUpdates;
    private JComboBox myUpdateChannels;
    private JButton myCheckNow;
    private JBLabel myChannelWarning;
    private JCheckBox myUseSecureConnection;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;

    public UpdatesSettingsPanel(boolean checkNowEnabled) {
      mySettings = UpdateSettings.getInstance();

      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      String majorVersion = appInfo.getMajorVersion();
      String versionNumber = "";
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        String minorVersion = appInfo.getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          versionNumber = majorVersion + "." + minorVersion;
        }
        else {
          versionNumber = majorVersion + ".0";
        }
      }
      myVersionNumber.setText(appInfo.getVersionName() + " " + versionNumber);
      myBuildNumber.setText(appInfo.getBuild().asString());

      LabelTextReplacingUtil.replaceText(myPanel);

      if (checkNowEnabled) {
        myCheckNow.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myCheckNow));
            UpdateSettings settings = new UpdateSettings();
            settings.loadState(mySettings.getState());
            settings.setSelectedChannelStatus(getSelectedChannelType());
            settings.setSecureConnection(myUseSecureConnection.isSelected());
            UpdateChecker.updateAndShowResult(project, settings);
            updateLastCheckedLabel();
          }
        });
      }
      else {
        myCheckNow.setVisible(false);
      }

      final ChannelStatus current = mySettings.getSelectedChannelStatus();
      //noinspection unchecked
      myUpdateChannels.setModel(new CollectionComboBoxModel<ChannelStatus>(Arrays.asList(ChannelStatus.values()), current));
      myUpdateChannels.setEnabled(
        !ApplicationInfoEx.getInstanceEx().isEAP() || !UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds());
      myUpdateChannels.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          boolean lessStable = current.compareTo(getSelectedChannelType()) > 0;
          myChannelWarning.setVisible(lessStable);
        }
      });
      myChannelWarning.setForeground(JBColor.RED);
    }

    private void updateLastCheckedLabel() {
      long time = mySettings.getLastTimeChecked();
      myLastCheckedDate.setText(time == 0 ? IdeBundle.message("updates.last.check.never") : DateFormatUtil.formatPrettyDateTime(time));
    }

    public ChannelStatus getSelectedChannelType() {
      return (ChannelStatus)myUpdateChannels.getSelectedItem();
    }

    public void setSelectedChannelType(ChannelStatus channelType) {
      myUpdateChannels.setSelectedItem(channelType != null ? channelType : ChannelStatus.RELEASE);
    }
  }
}