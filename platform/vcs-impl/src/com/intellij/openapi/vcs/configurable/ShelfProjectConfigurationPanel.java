// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
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
import java.util.Objects;

import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.DEFAULT_PROJECT_PRESENTATION_PATH;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getDefaultShelfPath;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.NORTHWEST;

public class ShelfProjectConfigurationPanel extends JPanel {
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final Project myProject;
  @NotNull private final JBCheckBox myBaseRevisionTexts;
  @NotNull private final JLabel myInfoLabel;

  public ShelfProjectConfigurationPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
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
                                                         JBUI.insets(3, 0), 0, 0);
    contentPanel.add(createStoreBaseRevisionOption(), gb);

    JPanel shelfConfigurablePanel = new JPanel(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));
    JButton shelfConfigurableButton = new JButton(VcsBundle.message("settings.change.shelves.location"));
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
    myInfoLabel.setText((myProject.isDefault() ? VcsBundle.message("settings.default.location")
                                               : VcsBundle.message("settings.current.location")) +
                        (myVcsConfiguration.USE_CUSTOM_SHELF_PATH ? FileUtil.toSystemDependentName(
                          Objects.requireNonNull(myVcsConfiguration.CUSTOM_SHELF_PATH)) : getDefaultShelfPresentationPath(myProject)));
  }

  /**
   * System dependent path to default shelf dir
   */
  static @NotNull String getDefaultShelfPresentationPath(@NotNull Project project) {
    return project.isDefault() ? DEFAULT_PROJECT_PRESENTATION_PATH : getDefaultShelfPath(project).toString();
  }

  private JComponent createStoreBaseRevisionOption() {
    final JBLabel noteLabel = new JBLabel(VcsBundle.message("settings.shelf.content.larger", ourMaximumFileForBaseRevisionSize / 1000));
    noteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    noteLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    noteLabel.setBorder(JBUI.Borders.empty(2, 25, 5, 0));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myBaseRevisionTexts, BorderLayout.NORTH);
    panel.add(noteLabel, BorderLayout.SOUTH);
    return panel;
  }

  public void restoreFromSettings() {
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
  }

  public boolean isModified() {
    return myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected();
  }

  public void apply() {
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
  }
}
