// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class UpdateSettingsConfigurable implements SearchableConfigurable {
  private final UpdateSettings mySettings;
  private final boolean myCheckNowEnabled;
  private UpdatesSettingsPanel myPanel;

  @SuppressWarnings("unused")
  UpdateSettingsConfigurable() {
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

  @Override
  public void apply() throws ConfigurationException {
    boolean wasEnabled = mySettings.isCheckNeeded();
    mySettings.setCheckNeeded(myPanel.myCheckForUpdates.isSelected());
    if (wasEnabled != mySettings.isCheckNeeded()) {
      UpdateCheckerComponent checker = UpdateCheckerComponent.getInstance();
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
  }

  @Override
  public void reset() {
    myPanel.myCheckForUpdates.setSelected(mySettings.isCheckNeeded());
    myPanel.updateLastCheckedLabel();
    myPanel.setSelectedChannelType(mySettings.getSelectedActiveChannel());
  }

  @Override
  public boolean isModified() {
    return myPanel != null &&
           (myPanel.myCheckForUpdates.isSelected() != mySettings.isCheckNeeded() ||
            myPanel.myUpdateChannels.getSelectedItem() != mySettings.getSelectedActiveChannel());
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private static class UpdatesSettingsPanel {
    private final UpdateSettings mySettings;
    private JPanel myPanel;
    private JCheckBox myCheckForUpdates;
    private JComboBox<ChannelStatus> myUpdateChannels;
    private JButton myCheckNow;
    private JBLabel myChannelWarning;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;
    @SuppressWarnings("unused") private ActionLink myIgnoredBuildsLink;

    UpdatesSettingsPanel(boolean checkNowEnabled) {
      mySettings = UpdateSettings.getInstance();

      ChannelStatus current = mySettings.getSelectedActiveChannel();
      myUpdateChannels.setModel(new CollectionComboBoxModel<>(mySettings.getActiveChannels(), current));

      ExternalUpdateManager manager = ExternalUpdateManager.ACTUAL;
      if (manager != null) {
        myCheckForUpdates.setText(IdeBundle.message("updates.settings.checkbox.external"));
        myUpdateChannels.setVisible(false);
        myChannelWarning.setText(IdeBundle.message("updates.settings.external", manager.toolName));
        myChannelWarning.setForeground(JBColor.GRAY);
        myChannelWarning.setVisible(true);
        myChannelWarning.setBorder(new JBEmptyBorder(0, 0, 10, 0));
      }
      else if (ApplicationInfoEx.getInstanceEx().isMajorEAP() && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
        myUpdateChannels.setEnabled(false);
        myUpdateChannels.setToolTipText(IdeBundle.message("updates.settings.channel.locked"));
      }
      else {
        myUpdateChannels.addActionListener(e -> {
          boolean lessStable = current.compareTo(getSelectedChannelType()) > 0;
          myChannelWarning.setVisible(lessStable);
        });
        myChannelWarning.setForeground(JBColor.RED);
      }

      if (checkNowEnabled) {
        myCheckNow.addActionListener(e -> {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myCheckNow));
          UpdateSettings settings = new UpdateSettings();
          settings.loadState(mySettings.getState());
          settings.setSelectedChannelStatus(getSelectedChannelType());
          UpdateChecker.updateAndShowResult(project, settings);
          updateLastCheckedLabel();
        });
      }
      else {
        myCheckNow.setVisible(false);
      }

      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      myVersionNumber.setText(ApplicationNamesInfo.getInstance().getFullProductName() + ' ' + appInfo.getFullVersion());
      myBuildNumber.setText(appInfo.getBuild().asString());
    }

    private void createUIComponents() {
      myIgnoredBuildsLink = new ActionLink(IdeBundle.message("updates.settings.ignored"), new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          List<String> buildNumbers = mySettings.getIgnoredBuildNumbers();
          String text = StringUtil.join(buildNumbers, "\n");
          String result = Messages.showMultilineInputDialog(null, null, IdeBundle.message("updates.settings.ignored.title"), text, null, null);
          if (result != null) {
            buildNumbers.clear();
            buildNumbers.addAll(StringUtil.split(result, "\n"));
          }
        }
      });
    }

    private void updateLastCheckedLabel() {
      long time = mySettings.getLastTimeChecked();
      if (time <= 0) {
        myLastCheckedDate.setText(IdeBundle.message("updates.last.check.never"));
      }
      else {
        myLastCheckedDate.setText(DateFormatUtil.formatPrettyDateTime(time));
        myLastCheckedDate.setToolTipText(DateFormatUtil.formatDate(time) + ' ' + DateFormatUtil.formatTimeWithSeconds(time));
      }
    }

    public ChannelStatus getSelectedChannelType() {
      return (ChannelStatus)myUpdateChannels.getSelectedItem();
    }

    public void setSelectedChannelType(ChannelStatus channelType) {
      myUpdateChannels.setSelectedItem(channelType != null ? channelType : ChannelStatus.RELEASE);
    }
  }
}