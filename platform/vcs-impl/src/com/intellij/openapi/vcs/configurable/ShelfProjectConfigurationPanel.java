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
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;

public class ShelfProjectConfigurationPanel extends JPanel {
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private TextFieldWithBrowseButton myShelfDirectoryPath;
  @NotNull private final JBCheckBox myBaseRevisionTexts;
  private JBCheckBox myUseCustomShelfDirectory;

  public ShelfProjectConfigurationPanel(Project project) {
    super(new GridBagLayout());
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myUseCustomShelfDirectory = new JBCheckBox("Use custom shelf storage directory");
    myBaseRevisionTexts = new JBCheckBox(VcsBundle.message("vcs.shelf.store.base.content"), myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
    layoutComponents();
  }

  void layoutComponents() {
    final GridBagConstraints gb = new GridBagConstraints();
    myShelfDirectoryPath = new TextFieldWithBrowseButton();
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.gridy++;
    add(createCustomShelfDirectoryPanel(), gb);
    gb.gridy++;
    add(createStoreBaseRevisionOption(), gb);
  }

  private JComponent createCustomShelfDirectoryPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myUseCustomShelfDirectory, BorderLayout.NORTH);
    JPanel pathPanel = new JPanel(new BorderLayout());
    JLabel pathLabel = new JBLabel("Path to shelf directory:");
    pathLabel.setLabelFor(myShelfDirectoryPath);
    pathPanel.add(pathLabel, BorderLayout.WEST);
    pathPanel.add(myShelfDirectoryPath, BorderLayout.CENTER);
    panel.add(pathPanel, BorderLayout.SOUTH);
    return panel;
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
    return false;
  }

  public void apply() {

  }
}
