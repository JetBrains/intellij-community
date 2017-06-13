/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.DEFAULT_PROJECT_PRESENTATION_PATH;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getDefaultShelfPath;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.NORTHWEST;

public class ShelfProjectConfigurationPanel extends JPanel {
  @NotNull private static final String CURRENT_LOCATION_HINT = "Current location is ";
  @NotNull private static final String DEFAULT_LOCATION_HINT = "Default location is ";
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final Project myProject;
  @NotNull private final JBCheckBox myShowCurrentFileChangesNotification;
  @NotNull private final JBCheckBox myBaseRevisionTexts;
  @NotNull private final JLabel myInfoLabel;

  public ShelfProjectConfigurationPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myShowCurrentFileChangesNotification = new JBCheckBox(VcsBundle.message("vcs.shelf.current.file.changes.on.shelf.notification"));
    myBaseRevisionTexts = new JBCheckBox(VcsBundle.message("vcs.shelf.store.base.content"));
    myInfoLabel = new JLabel();
    myInfoLabel.setBorder(null);
    myInfoLabel.setForeground(JBColor.GRAY);
    initComponents();
    layoutComponents();
  }

  private void initComponents() {
    restoreFromSettings();
    myBaseRevisionTexts.setMnemonic('b');
    updateLabelInfo();
  }

  private void layoutComponents() {
    JPanel contentPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 1, NORTHWEST, NONE,
                                                         JBUI.insets(3), 0, 0);
    contentPanel.add(myShowCurrentFileChangesNotification, gb);
    gb.gridy++;
    contentPanel.add(createStoreBaseRevisionOption(), gb);

    JPanel shelfConfigurablePanel = new JPanel(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));
    JButton shelfConfigurableButton = new JButton("Change Shelves Location");
    shelfConfigurableButton.setEnabled(!myProject.isDefault());
    shelfConfigurableButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (new ShelfStorageConfigurationDialog(myProject).showAndGet()) {
          updateLabelInfo();
        }
      }
    });
    shelfConfigurablePanel.add(shelfConfigurableButton, BorderLayout.WEST);
    shelfConfigurablePanel.add(myInfoLabel, BorderLayout.CENTER);
    gb.gridy++;
    contentPanel.add(shelfConfigurablePanel, gb);
    add(contentPanel, BorderLayout.NORTH);
  }

  private void updateLabelInfo() {
    myInfoLabel.setText((myProject.isDefault() ? DEFAULT_LOCATION_HINT : CURRENT_LOCATION_HINT) +
                        (myVcsConfiguration.USE_CUSTOM_SHELF_PATH ? toSystemDependentName(
                          assertNotNull(myVcsConfiguration.CUSTOM_SHELF_PATH)) : getDefaultShelfPresentationPath(myProject)));
  }

  /**
   * System dependent path to default shelf dir
   */
  @NotNull
  static String getDefaultShelfPresentationPath(@NotNull Project project) {
    return toSystemDependentName(project.isDefault() ? DEFAULT_PROJECT_PRESENTATION_PATH : getDefaultShelfPath(project));
  }

  private JComponent createStoreBaseRevisionOption() {
    final JBLabel noteLabel =
      new JBLabel("The base content of files larger than " + ourMaximumFileForBaseRevisionSize / 1000 + "K will not be stored");
    noteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    noteLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    noteLabel.setBorder(JBUI.Borders.empty(2, 25, 5, 0));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myBaseRevisionTexts, BorderLayout.NORTH);
    panel.add(noteLabel, BorderLayout.SOUTH);
    return panel;
  }

  public void restoreFromSettings() {
    myShowCurrentFileChangesNotification.setSelected(myVcsConfiguration.SHOW_CURRENT_FILE_CHANGES_ON_SHELF);
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
  }

  public boolean isModified() {
    return myVcsConfiguration.SHOW_CURRENT_FILE_CHANGES_ON_SHELF != myShowCurrentFileChangesNotification.isSelected() ||
           myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected();
  }

  public void apply() {
    if (myVcsConfiguration.SHOW_CURRENT_FILE_CHANGES_ON_SHELF != myShowCurrentFileChangesNotification.isSelected()) {
      EditorNotifications.getInstance(myProject).updateAllNotifications();
    }
    myVcsConfiguration.SHOW_CURRENT_FILE_CHANGES_ON_SHELF = myShowCurrentFileChangesNotification.isSelected();
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
  }
}
