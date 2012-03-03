/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.profile;

import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ProfileDialog extends DialogWrapper {
  private final JPanel myContentPanel;
  private final JBList myList;

  private final ProfileElement myDeviceElement;
  private final ProfileElement myDeviceConfigurationElement;
  private final ProfileElement myTargetElement;
  private final ProfileElement myLocaleElement;
  private final ProfileElement myDockModeElement;
  private final ProfileElement myNightModeElement;
  private final ProfileElement myThemeElement;

  private final ProfileManager myProfileManager;

  public ProfileDialog(Module module) {
    super(module.getProject(), false);

    setTitle("Edit Profiles");
    setResizable(false);

    myList = new JBList();

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // TODO: Auto-generated method stub
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // TODO: Auto-generated method stub
      }
    });

    myProfileManager = new ProfileManager(module, new Runnable() {
      @Override
      public void run() {
        // TODO: Auto-generated method stub
      }
    });

    JPanel profilePanel = new JPanel(new GridBagLayout());

    profilePanel.add(new JBLabel("Profile name:"),
                     new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                                            new Insets(0, 0, 10, 0),
                                            0, 0));
    JTextField textField = new JTextField();
    textField.setEditable(false);
    profilePanel.add(textField,
                     new GridBagConstraints(1, 0, 2, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                            new Insets(0, 0, 10, 0),
                                            0, 0));

    myDeviceElement = new ProfileElement(profilePanel, "Device:", myProfileManager.getDeviceAction());
    myDeviceConfigurationElement = new ProfileElement(profilePanel, "Configuration:", myProfileManager.getDeviceConfigurationAction());
    myTargetElement = new ProfileElement(profilePanel, "Target:", myProfileManager.getTargetAction());
    myLocaleElement = new ProfileElement(profilePanel, "Locale:", myProfileManager.getLocaleAction());
    myDockModeElement = new ProfileElement(profilePanel, "Dock Mode:", myProfileManager.getDockModeAction());
    myNightModeElement = new ProfileElement(profilePanel, "Night Mode:", myProfileManager.getNightModeAction());
    myThemeElement = new ProfileElement(profilePanel, "Theme:", myProfileManager.getThemeAction());

    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(decorator.createPanel(), BorderLayout.PAGE_START);
    myContentPanel.add(profilePanel, BorderLayout.PAGE_END);
    myContentPanel.setPreferredSize(new Dimension(500, 375));

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  private static class ProfileElement {
    private ProfileElement(JPanel parent, String name, ComboBoxAction action) {
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.LINE_START;

      gbc.gridx = 0;
      parent.add(new JLabel(name), gbc);

      gbc.gridx++;
      gbc.weightx = 0.4;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      parent.add(action.createCustomComponent(action.getTemplatePresentation()), gbc);

      gbc.gridx++;
      gbc.weightx = 0.6;
      gbc.fill = GridBagConstraints.NONE;
      gbc.insets = new Insets(0, 10, 0, 0);
      parent.add(new JBCheckBox("Visible", true), gbc);
    }
  }
}