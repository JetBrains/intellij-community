package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

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
  private JRadioButton[] myOnFileMergedWithConflictGroup;

  public CvsConfigurationPanel(Project project) {
    myOnFileMergedWithConflictGroup = new JRadioButton[]{
      myShowDialogOnMergedWithConflict,
      myGetLatestVersionOnMergedWithConflict,
      mySkipOnMergedWithConflict
    };

    myConfigureGlobalButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        ConfigureCvsGlobalSettingsDialog dialog = new ConfigureCvsGlobalSettingsDialog();
        dialog.show();
      }
    });

  }

  public static void addBrowseHandler(final TextFieldWithBrowseButton field, final String title) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    descriptor.setTitle(title);
    field.addBrowseFolderListener(null, null, null, descriptor, new TextComponentAccessor<JTextField>() {
      public String getText(JTextField textField) {
        String text = textField.getText();
        if (text.length() > 0) text = CvsApplicationLevelConfiguration.convertToIOFilePath(text);
        return text;
      }

      public void setText(JTextField textField, String text) {
        textField.setText(text);
      }
    });
  }

  public void updateFrom(CvsConfiguration config,
                         CvsApplicationLevelConfiguration appLevelConfiguration) {
    myConfigurations = new ArrayList<CvsRootConfiguration>(appLevelConfiguration.CONFIGURATIONS);


    myShowOutput.setSelected(config.SHOW_OUTPUT);
    myMakeNewFilesReadOnly.setSelected(config.MAKE_NEW_FILES_READONLY);

    myOnFileMergedWithConflictGroup[config.SHOW_CORRUPTED_PROJECT_FILES].setSelected(true);

    //noinspection MismatchedQueryAndUpdateOfCollection
    KeywordSubstitutionListWithSelection keywordSubstitutions = new KeywordSubstitutionListWithSelection();
    myDefaultTextFileKeywordSubstitution.removeAllItems();
    for (final KeywordSubstitutionWrapper keywordSubstitution : keywordSubstitutions) {
      myDefaultTextFileKeywordSubstitution.addItem(keywordSubstitution);
    }
    myDefaultTextFileKeywordSubstitution.setSelectedItem(
      KeywordSubstitutionWrapper.getValue(config.DEFAULT_TEXT_FILE_SUBSTITUTION));
  }

  private static int getSelected(JRadioButton[] group) {
    for (int i = 0; i < group.length; i++) {
      JRadioButton jRadioButton = group[i];
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
    return ((KeywordSubstitutionWrapper)myDefaultTextFileKeywordSubstitution.getSelectedItem()).getSubstitution()
      .toString();
  }

  public boolean equalsTo(CvsConfiguration config,
                          CvsApplicationLevelConfiguration appLevelConfiguration) {
    return new HashSet<CvsRootConfiguration>(appLevelConfiguration.CONFIGURATIONS).equals(new HashSet<CvsRootConfiguration>(myConfigurations))
           && config.MAKE_NEW_FILES_READONLY == myMakeNewFilesReadOnly.isSelected()
           && config.SHOW_OUTPUT == myShowOutput.isSelected()
           && config.SHOW_CORRUPTED_PROJECT_FILES == getSelected(myOnFileMergedWithConflictGroup)
           && config.DEFAULT_TEXT_FILE_SUBSTITUTION.equals(selectedSubstitution());
  }

  public JComponent getPanel() {
    return myPanel;
  }

}
