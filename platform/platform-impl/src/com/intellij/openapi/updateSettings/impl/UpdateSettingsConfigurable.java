/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ListUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author pti
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private UpdatesSettingsPanel myUpdatesSettingsPanel;
  private boolean myCheckNowEnabled = true;

  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  public String getDisplayName() {
    return IdeBundle.message("updates.settings.title");
  }

  public String getHelpTopic() {
    return "preferences.updates";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableUpdates.png");
  }

  public void setCheckNowEnabled(boolean enabled) {
    myCheckNowEnabled = enabled;
  }

  public void apply() throws ConfigurationException {
    UpdateSettings settings = UpdateSettings.getInstance();
    settings.CHECK_NEEDED = myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected();

    settings.myPluginHosts.clear();
    settings.myPluginHosts.addAll(myUpdatesSettingsPanel.getPluginsHosts());
  }

  public void reset() {
    UpdateSettings settings = UpdateSettings.getInstance();
    myUpdatesSettingsPanel.myCbCheckForUpdates.setSelected(settings.CHECK_NEEDED);
    myUpdatesSettingsPanel.updateLastCheckedLabel();
    myUpdatesSettingsPanel.setPluginHosts(settings.myPluginHosts);
  }

  public boolean isModified() {
    UpdateSettings settings = UpdateSettings.getInstance();
    if (!settings.myPluginHosts.equals(myUpdatesSettingsPanel.getPluginsHosts())) return true;
    return settings.CHECK_NEEDED != myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected();
  }

  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  public Collection<? extends String> getPluginsHosts() {
    return myUpdatesSettingsPanel.getPluginsHosts();
  }

  private class UpdatesSettingsPanel {

    private JPanel myPanel;
    private JButton myBtnCheckNow;
    private JCheckBox myCbCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;

    private JButton myAddButton;
    private JButton myDeleteButton;
    private JList myUrlsList;
    private JButton myEditButton;

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

      myBtnCheckNow.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          CheckForUpdateAction.actionPerformed(false, UpdateSettingsConfigurable.this);
          updateLastCheckedLabel();
        }
      });
      myBtnCheckNow.setEnabled(myCheckNowEnabled);

      LabelTextReplacingUtil.replaceText(myPanel);

      myUrlsList.setModel(new DefaultListModel());
      myUrlsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myUrlsList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          myDeleteButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
          myEditButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
        }
      });

      myAddButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          final HostMessages.InputHostDialog dlg = new HostMessages.InputHostDialog(myPanel,
                                                                                    IdeBundle.message("update.plugin.host.url.message"),
                                                                                    IdeBundle.message("update.add.new.plugin.host.title"),
                                                                                    Messages.getQuestionIcon(), "",
                                                                                    new NonEmptyInputValidator());
          dlg.show();
          final String input = dlg.getInputString();
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).addElement(input);
          }
        }
      });

      myEditButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          final HostMessages.InputHostDialog dlg = new HostMessages.InputHostDialog(myPanel,
                                                                                    IdeBundle.message("update.plugin.host.url.message"),
                                                                                    IdeBundle.message("update.edit.plugin.host.title"),
                                                                                    Messages.getQuestionIcon(),
                                                                                    (String)myUrlsList.getSelectedValue(),
                                                                                    new InputValidator() {
                                                                                      public boolean checkInput(final String inputString) {
                                                                                        return inputString.length() > 0;
                                                                                      }

                                                                                      public boolean canClose(final String inputString) {
                                                                                        return checkInput(inputString);
                                                                                      }
                                                                                    });
          dlg.show();
          final String input = dlg.getInputString();
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).set(myUrlsList.getSelectedIndex(), input);
          }
        }
      });

      myDeleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          ListUtil.removeSelectedItems(myUrlsList);
        }
      });
      myEditButton.setEnabled(false);
      myDeleteButton.setEnabled(false);
    }

    private void updateLastCheckedLabel() {
      final long lastChecked = UpdateSettings.getInstance().LAST_TIME_CHECKED;
      myLastCheckedDate
        .setText(lastChecked == 0 ? IdeBundle.message("updates.last.check.never") : DateFormatUtil.formatPrettyDateTime(lastChecked));
    }

    public List<String> getPluginsHosts() {
      final List<String> result = new ArrayList<String>();
      for (int i = 0;i < myUrlsList.getModel().getSize(); i++) {
        result.add((String)myUrlsList.getModel().getElementAt(i));
      }
      return result;
    }

    public void setPluginHosts(final List<String> pluginHosts) {
      final DefaultListModel model = (DefaultListModel)myUrlsList.getModel();
      model.clear();
      for (String host : pluginHosts) {
        model.addElement(host);
      }
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public static class HostMessages extends Messages {
    public static class InputHostDialog extends InputDialog {
      private final Component myParentComponent;

      public InputHostDialog(Component parentComponent, String message, String title, Icon icon, String initialValue, InputValidator validator) {
        super(parentComponent, message, title, icon, initialValue, validator);
        myParentComponent = parentComponent;
      }

      protected Action[] createActions() {
        final Action[] actions = super.createActions();
        return ArrayUtil.append(actions, new AbstractAction("Check Now") {
          public void actionPerformed(final ActionEvent e) {
            try {
              if (UpdateChecker.checkPluginsHost(getTextField().getText(), new ArrayList<PluginDownloader>())) {
                showInfoMessage(myParentComponent, "Plugins Host was successfully checked", "Check Plugins Host");
              } else {
                showErrorDialog(myParentComponent, "Plugin descriptions contain some errors. Please, check idea.log for details.");
              }
            }
            catch (Exception e1) {
              showErrorDialog(myParentComponent, "Connection failed: " + e1.getMessage());
            }
          }
        });
      }
    }
  }

}
