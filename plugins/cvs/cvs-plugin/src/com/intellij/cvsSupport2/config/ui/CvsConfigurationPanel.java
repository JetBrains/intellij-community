/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * author: lesya
 */
public class CvsConfigurationPanel {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel");

  private JPanel myPanel;

  private JCheckBox myMakeNewFilesReadOnly;
  private JComboBox myDefaultTextFileKeywordSubstitution;
  private JCheckBox myShowOutput;

  private ArrayList<CvsRootConfiguration> myConfigurations;
  private JButton myConfigureGlobalButton;

  private JRadioButton myGetLatestVersionOnMergedWithConflict;
  private JRadioButton mySkipOnMergedWithConflict;
  private JRadioButton myShowDialogOnMergedWithConflict;
  private final JRadioButton[] myOnFileMergedWithConflictGroup;

  public CvsConfigurationPanel(final Project project) {
    myOnFileMergedWithConflictGroup = new JRadioButton[]{
      myShowDialogOnMergedWithConflict,
      myGetLatestVersionOnMergedWithConflict,
      mySkipOnMergedWithConflict
    };

    myConfigureGlobalButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        final ConfigureCvsGlobalSettingsDialog dialog = new ConfigureCvsGlobalSettingsDialog(project);
        dialog.show();
      }
    });
  }

  public static void addBrowseHandler(Project project, final TextFieldWithBrowseButton field, final String title) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    field.addBrowseFolderListener(title, null, project, descriptor, new TextComponentAccessor<JTextField>() {
      public String getText(JTextField textField) {
        String text = textField.getText();
        if (text.length() > 0) {
          text = CvsApplicationLevelConfiguration.convertToIOFilePath(text);
        }
        return text;
      }

      public void setText(JTextField textField, @NotNull String text) {
        textField.setText(text);
      }
    });
  }

  public void updateFrom(CvsConfiguration config, CvsApplicationLevelConfiguration appLevelConfiguration) {
    myConfigurations = new ArrayList<>(appLevelConfiguration.CONFIGURATIONS);
    myShowOutput.setSelected(config.SHOW_OUTPUT);
    myMakeNewFilesReadOnly.setSelected(config.MAKE_NEW_FILES_READONLY);
    myOnFileMergedWithConflictGroup[config.SHOW_CORRUPTED_PROJECT_FILES].setSelected(true);

    myDefaultTextFileKeywordSubstitution.removeAllItems();
    for (final KeywordSubstitutionWrapper keywordSubstitution : KeywordSubstitutionWrapper.values()) {
      myDefaultTextFileKeywordSubstitution.addItem(keywordSubstitution);
    }
    myDefaultTextFileKeywordSubstitution.setSelectedItem(
      KeywordSubstitutionWrapper.getValue(config.DEFAULT_TEXT_FILE_SUBSTITUTION));
  }

  private static int getSelected(JRadioButton[] group) {
    for (int i = 0; i < group.length; i++) {
      final JRadioButton jRadioButton = group[i];
      if (jRadioButton.isSelected()) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  public void saveTo(CvsConfiguration config, CvsApplicationLevelConfiguration appLevelConfiguration) {
    appLevelConfiguration.CONFIGURATIONS = myConfigurations;

    config.MAKE_NEW_FILES_READONLY = myMakeNewFilesReadOnly.isSelected();
    config.DEFAULT_TEXT_FILE_SUBSTITUTION = selectedSubstitution();

    config.SHOW_OUTPUT = myShowOutput.isSelected();
    config.SHOW_CORRUPTED_PROJECT_FILES = getSelected(myOnFileMergedWithConflictGroup);
  }

  private String selectedSubstitution() {
    return ((KeywordSubstitutionWrapper)myDefaultTextFileKeywordSubstitution.getSelectedItem()).getSubstitution().toString();
  }

  public boolean equalsTo(CvsConfiguration config,
                          CvsApplicationLevelConfiguration appLevelConfiguration) {
    return new HashSet<>(appLevelConfiguration.CONFIGURATIONS).equals(new HashSet<>(myConfigurations))
           && config.MAKE_NEW_FILES_READONLY == myMakeNewFilesReadOnly.isSelected()
           && config.SHOW_OUTPUT == myShowOutput.isSelected()
           && config.SHOW_CORRUPTED_PROJECT_FILES == getSelected(myOnFileMergedWithConflictGroup)
           && config.DEFAULT_TEXT_FILE_SUBSTITUTION.equals(selectedSubstitution());
  }

  public JComponent getPanel() {
    return myPanel;
  }

}
