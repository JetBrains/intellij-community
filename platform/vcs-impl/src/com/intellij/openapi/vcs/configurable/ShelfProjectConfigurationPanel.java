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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;
import static java.awt.GridBagConstraints.*;

public class ShelfProjectConfigurationPanel extends JPanel {
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final Project myProject;
  @NotNull private final String myDefaultPresentationPathString;
  @NotNull private final JBCheckBox myUseCustomShelfDirectory;
  @NotNull private final JBLabel myShelfDirectoryLabel;
  @NotNull private final TextFieldWithBrowseButton myShelfDirectoryPath;
  @NotNull private final JBCheckBox myBaseRevisionTexts;
  @NotNull private final JBCheckBox myMoveShelvesCheckBox;

  public ShelfProjectConfigurationPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myUseCustomShelfDirectory = new JBCheckBox("Use custom shelf storage directory");
    myShelfDirectoryLabel = new JBLabel("Shelf directory:");
    myShelfDirectoryLabel.setFocusable(false);
    myShelfDirectoryPath = new TextFieldWithBrowseButton();
    myShelfDirectoryPath.addBrowseFolderListener("Shelf", "Select a directory to store shelves in", myProject,
                                                 FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myShelfDirectoryPath.getTextField().setInputVerifier(new InputVerifier() {
      @Override
      public boolean verify(JComponent input) {
        File file = new File(myShelfDirectoryPath.getText());
        String errorMessage = ShelveChangesManager.validateDestinationDirectory(file);
        if (errorMessage != null && myShelfDirectoryPath.isShowing()) {
          PopupUtil.showBalloonForComponent(myShelfDirectoryPath, errorMessage, MessageType.WARNING, false, myProject);
        }
        return errorMessage != null;
      }
    });
    myMoveShelvesCheckBox = new JBCheckBox(VcsBundle.message("vcs.shelf.move.text")); 
    myBaseRevisionTexts = new JBCheckBox(VcsBundle.message("vcs.shelf.store.base.content"));
    myDefaultPresentationPathString = ShelveChangesManager.getDefaultShelfPresentationPath(project);
    initComponents();
    layoutComponents();
  }

  private void initComponents() {
    restoreFromSettings();
    myUseCustomShelfDirectory.setMnemonic('U');
    myBaseRevisionTexts.setMnemonic('b');
    myMoveShelvesCheckBox.setMnemonic('M');
    setEnabledCustomShelfDirectoryComponents(myUseCustomShelfDirectory.isSelected());
    myUseCustomShelfDirectory.addChangeListener(e -> {
      boolean useCustomDir = myUseCustomShelfDirectory.isSelected();
      setEnabledCustomShelfDirectoryComponents(useCustomDir);
    });
  }

  private void setEnabledCustomShelfDirectoryComponents(boolean enabled) {
    myShelfDirectoryPath.setEnabled(enabled);
    myShelfDirectoryPath.setEditable(enabled);
    myShelfDirectoryLabel.setEnabled(enabled);
    if (!enabled) {
      myShelfDirectoryPath.setText(myDefaultPresentationPathString);
    }
    else if (myProject.isDefault()) {
      myShelfDirectoryPath.setText("");
    }
  }

  private void layoutComponents() {
    JPanel contentPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 1, NORTHWEST, NONE,
                                                         JBUI.insets(3), 0, 0);
    contentPanel.add(myUseCustomShelfDirectory, gb);
    gb.gridy++;
    gb.fill = HORIZONTAL;
    contentPanel.add(createCustomShelfDirectoryPanel(), gb);
    gb.gridy++;
    gb.fill = NONE;
    contentPanel.add(myMoveShelvesCheckBox, gb);
    gb.gridy++;
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

  public void restoreFromSettings() {
    myUseCustomShelfDirectory.setSelected(myVcsConfiguration.USE_CUSTOM_SHELF_PATH);
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
    myShelfDirectoryPath
      .setText(myVcsConfiguration.USE_CUSTOM_SHELF_PATH ? myVcsConfiguration.CUSTOM_SHELF_PATH : myDefaultPresentationPathString);
    myMoveShelvesCheckBox.setSelected(myVcsConfiguration.MOVE_SHELVES);
  }

  public boolean isModified() {
    if (myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected()) return true;
    if (myVcsConfiguration.USE_CUSTOM_SHELF_PATH != myUseCustomShelfDirectory.isSelected()) return true;
    if (!StringUtil.equals(myVcsConfiguration.CUSTOM_SHELF_PATH, myShelfDirectoryPath.getText())) return true;
    if(myVcsConfiguration.MOVE_SHELVES != myMoveShelvesCheckBox.isSelected()) return true;
    return false;
  }

  public void apply() throws ConfigurationException {
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
    boolean customShelfDir = myUseCustomShelfDirectory.isSelected();
    String prevShelfPath = myVcsConfiguration.CUSTOM_SHELF_PATH;
    myVcsConfiguration.USE_CUSTOM_SHELF_PATH = customShelfDir;
    myVcsConfiguration.CUSTOM_SHELF_PATH = customShelfDir ? myShelfDirectoryPath.getText() : null;
    myVcsConfiguration.MOVE_SHELVES = myMoveShelvesCheckBox.isSelected();
    if (!myProject.isDefault()) {
      myProject.save();
      if (prevShelfPath != null) {
        ApplicationManager.getApplication().saveSettings();
      }
      ShelveChangesManager.getInstance(myProject).checkAndMigrateUnderProgress(prevShelfPath, myVcsConfiguration.CUSTOM_SHELF_PATH);
    }
  }
}
