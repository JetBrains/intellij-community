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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.GridBagConstraints;

import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;
import static java.awt.GridBagConstraints.*;

public class ShelfProjectConfigurationPanel extends JPanel {
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private Project myProject;
  @NotNull private final String myDefaultPresentationPathString;
  @NotNull private JBCheckBox myUseCustomShelfDirectory;
  @NotNull private JBLabel myShelfDirectoryLabel;
  @NotNull private TextFieldWithBrowseButton myShelfDirectoryPath;
  @NotNull private final JCheckBox myBaseRevisionTexts;

  public ShelfProjectConfigurationPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myUseCustomShelfDirectory = new JBCheckBox("Use custom shelf storage directory");
    myShelfDirectoryLabel = new JBLabel("Shelf directory:");
    myShelfDirectoryPath = new TextFieldWithBrowseButton();
    myBaseRevisionTexts = new JCheckBox(VcsBundle.message("vcs.shelf.store.base.content"));
    myDefaultPresentationPathString = ShelveChangesManager.getDefaultShelfPresentationPath(project);
    initComponents();
    layoutComponents();
  }

  private void initComponents() {
    myUseCustomShelfDirectory.setSelected(myVcsConfiguration.USE_CUSTOM_SHELF_PATH);
    myUseCustomShelfDirectory.setMnemonic('U');
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
    myBaseRevisionTexts.setMnemonic('b');
    setEnabledCustomShelfDirectoryComponents();
    myUseCustomShelfDirectory.addActionListener(e -> {
      boolean useCustomDir = myUseCustomShelfDirectory.isSelected();
      if (useCustomDir) {
        IdeFocusManager.findInstance().requestFocus(myShelfDirectoryPath, true);
      }
      setEnabledCustomShelfDirectoryComponents();
    });
  }

  private void setEnabledCustomShelfDirectoryComponents() {
    boolean useCustomDir = myUseCustomShelfDirectory.isSelected();
    myShelfDirectoryPath.setEnabled(useCustomDir);
    myShelfDirectoryPath.setEditable(useCustomDir);
    myShelfDirectoryLabel.setEnabled(useCustomDir);
    if (!useCustomDir) {
      myShelfDirectoryPath.setText(myDefaultPresentationPathString);
    }
    else if (myProject.isDefault()) {
      myShelfDirectoryPath.setText("");
    }
  }

  private void layoutComponents() {
    JPanel contentPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, NORTHWEST, NONE,
                                                         JBUI.insets(0), 0, 0);
    contentPanel.add(myUseCustomShelfDirectory, gb);
    gb.gridy++;
    gb.fill = HORIZONTAL;
    contentPanel.add(createCustomShelfDirectoryPanel(), gb);
    gb.gridy++;
    gb.fill = NONE;
    contentPanel.add(createStoreBaseRevisionOption(), gb);
    add(contentPanel, BorderLayout.NORTH);
  }

  private JComponent createCustomShelfDirectoryPanel() {
    JPanel pathPanel = new JPanel(new BorderLayout());
    myShelfDirectoryLabel.setLabelFor(myShelfDirectoryPath);
    myShelfDirectoryLabel.setBorder(JBUI.Borders.emptyLeft(25));
    pathPanel.add(myShelfDirectoryLabel, BorderLayout.WEST);
    pathPanel.add(myShelfDirectoryPath, BorderLayout.CENTER);
    return pathPanel;
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

  public boolean isModified() {
    if (myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected()) return true;
    if (myVcsConfiguration.USE_CUSTOM_SHELF_PATH != myUseCustomShelfDirectory.isSelected()) return true;
    return false;
  }

  public void apply() {
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
    myVcsConfiguration.USE_CUSTOM_SHELF_PATH = myUseCustomShelfDirectory.isSelected();
  }
}
