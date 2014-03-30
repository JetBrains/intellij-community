/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PreferencesDialog extends DialogWrapper {
  private JPanel myRoot;
  private JPanel myTopPanel;
  private JPanel myCenterPanel;
  private SearchTextField mySearchTextField;

  public PreferencesDialog(@Nullable Project project, ConfigurableGroup[] groups) {
    super(project);
    init();
    ((JDialog)getPeer().getWindow()).setUndecorated(true);
    if (SystemInfo.isMac) {
      ((JComponent)((JDialog)getPeer().getWindow()).getContentPane()).setBorder(new EmptyBorder(0, 0, 0, 0));
    }
    else {
      ((JComponent)((JDialog)getPeer().getWindow()).getContentPane()).setBorder(new LineBorder(Gray._140, 1));
    }

    setTitle("Preferences");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(null);
    panel.add(createApplicationSettings());
    panel.add(createProjectSettings());
    panel.add(createEditorSettings());
    panel.add(createOtherSettings());
    panel.setPreferredSize(new Dimension(700, 370));
    myCenterPanel.add(panel, BorderLayout.CENTER);
    return myRoot;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchTextField.getTextEditor();
  }

  private static JComponent createEditorSettings() {
    final LabeledButtonsPanel panel = new LabeledButtonsPanel("Editor");
    panel.addButton(new PreferenceButton("Editor", AllIcons.Preferences.Editor));
    panel.addButton(new PreferenceButton("Code Style", AllIcons.Preferences.CodeStyle));
    panel.setBackground(Gray._229);
    panel.setBorder(new CustomLineBorder(Gray._223, 0, 0, 1, 0));
    return panel;
  }

  private static JComponent createProjectSettings() {
    final LabeledButtonsPanel panel = new LabeledButtonsPanel("Project");
    panel.addButton(new PreferenceButton("Compiler", AllIcons.Preferences.Compiler));
    panel.addButton(new PreferenceButton("Version Control", AllIcons.Preferences.VersionControl));
    panel.addButton(new PreferenceButton("File Colors", AllIcons.Preferences.FileColors));
    panel.addButton(new PreferenceButton("Scopes", AllIcons.Preferences.Editor));
    panel.setBackground(Gray._236);
    panel.setBorder(new CustomLineBorder(Gray._223, 0, 0, 1, 0));
    return panel;
  }

  private static JComponent createApplicationSettings() {
    final LabeledButtonsPanel panel = new LabeledButtonsPanel("IDE");
    panel.addButton(new PreferenceButton("Appearance", AllIcons.Preferences.Appearance));
    panel.addButton(new PreferenceButton("General", AllIcons.Preferences.General));
    panel.addButton(new PreferenceButton("Keymap", AllIcons.Preferences.Keymap));
    panel.addButton(new PreferenceButton("File Types", AllIcons.Preferences.FileTypes));
    panel.setBackground(Gray._229);
    panel.setBorder(new CustomLineBorder(Gray._223, 0, 0, 1, 0));
    return panel;
  }

  private static JComponent createOtherSettings() {
    final LabeledButtonsPanel panel = new LabeledButtonsPanel("Other");
    panel.addButton(new PreferenceButton("Plugins", AllIcons.Preferences.Plugins));
    panel.addButton(new PreferenceButton("Updates", AllIcons.Preferences.Updates));
    panel.setBackground(Gray._236);
    panel.setBorder(new CustomLineBorder(Gray._223, 0, 0, 1, 0));
    return panel;
  }


  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    if (SystemInfo.isMac) {
      return null;
    }
    final JComponent panel = super.createSouthPanel();
    if (panel != null) {
      panel.setBorder(new EmptyBorder(5, 5, 10, 20));
    }
    return panel;
  }

  private void createUIComponents() {
    myTopPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, Gray._206, 0, getHeight() - 1, Gray._172));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Gray._145);
        g.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
        g.setColor(Gray._103);
        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      }
    };
    final JLabel title = new JLabel("Preferences");
    if (!SystemInfo.isMac) {
      title.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, 14));
    }
    else {
      title.setFont(new Font("Lucuda Grande", Font.PLAIN, 12));
    }
    title.setHorizontalTextPosition(SwingConstants.CENTER);
    title.setHorizontalAlignment(SwingConstants.CENTER);
    title.setVerticalAlignment(SwingConstants.TOP);
    myTopPanel.add(title, BorderLayout.NORTH);
    mySearchTextField = new SearchTextField();
    mySearchTextField.setOpaque(false);
    myTopPanel.add(mySearchTextField, BorderLayout.EAST);
  }
}
