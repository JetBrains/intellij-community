// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class ImportSettingsStep extends WizardStep {

  private JPanel myPanel;
  private JTextField myModuleName;
  private JTextField myVendor;
  private JTextField myReleaseTag;
  private JTextArea myLogMessage;
  private JCheckBox myCheckoutAfterImport;
  private JCheckBox myMakeCheckedOutFilesReadOnly;
  private JLabel myModuleNameErrorMessage;
  private JLabel myVendorErrorMessage;
  private JLabel myReleaseTagErrorMessage;
  private JLabel myModuleNameLabel;
  private JLabel myVendorLabel;
  private JLabel myReleaseTagLabel;
  private JLabel myLogMessageLabel;
  private JButton myKeywordExpansionButton;

  private final SelectImportLocationStep mySelectImportLocationStep;
  private final ImportConfiguration myImportConfiguration;

  private File myDirectoryToImport;

  public ImportSettingsStep(final Project project,
                            CvsWizard wizard,
                            SelectImportLocationStep selectImportLocationStep,
                            final ImportConfiguration importConfiguration) {
    super(CvsBundle.message("dialog.title.import.settings"), wizard);

    myCheckoutAfterImport.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateCheckoutSettingsVisibility();
      }
    });
    mySelectImportLocationStep = selectImportLocationStep;
    myImportConfiguration = importConfiguration;

    myModuleNameLabel.setLabelFor(myModuleName);
    myReleaseTagLabel.setLabelFor(myReleaseTag);
    myVendorLabel.setLabelFor(myVendor);
    myLogMessageLabel.setLabelFor(myLogMessage);

    myLogMessage.setWrapStyleWord(true);
    myLogMessage.setLineWrap(true);

    myReleaseTag.setText(myImportConfiguration.RELEASE_TAG);
    myVendor.setText(myImportConfiguration.VENDOR);
    myLogMessage.setText(myImportConfiguration.LOG_MESSAGE);
    myCheckoutAfterImport.setSelected(myImportConfiguration.CHECKOUT_AFTER_IMPORT);
    myMakeCheckedOutFilesReadOnly.setSelected(myImportConfiguration.MAKE_NEW_FILES_READ_ONLY);
    updateCheckoutSettingsVisibility();
    selectAll();
    final DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        getWizard().updateButtons();
      }
    };
    myModuleName.getDocument().addDocumentListener(listener);
    myVendor.getDocument().addDocumentListener(listener);
    myReleaseTag.getDocument().addDocumentListener(listener);

    myKeywordExpansionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final CustomizeKeywordSubstitutionDialog dialog =
          new CustomizeKeywordSubstitutionDialog(project, CvsBundle.message("dialog.title.customize.keyword.substitutions"),
                                                 importConfiguration);
        dialog.show();
      }
    });

    init();
  }

  private void updateCheckoutSettingsVisibility() {
    myMakeCheckedOutFilesReadOnly.setEnabled(myCheckoutAfterImport.isSelected());
  }

  @Override
  public void saveState() {
    super.saveState();
    myImportConfiguration.RELEASE_TAG = getReleaseTag();
    myImportConfiguration.VENDOR = getVendor();
    myImportConfiguration.LOG_MESSAGE = getLogMessage();
    myImportConfiguration.CHECKOUT_AFTER_IMPORT = myCheckoutAfterImport.isSelected();
    myImportConfiguration.MAKE_NEW_FILES_READ_ONLY = myMakeCheckedOutFilesReadOnly.isSelected();
  }

  @Override
  public boolean nextIsEnabled() {
    final JTextField[] fields = new JTextField[]{myReleaseTag, myVendor};
    boolean result = CvsFieldValidator.checkField(myVendor, fields, true, myVendorErrorMessage, null);
    result &= CvsFieldValidator.checkField(myReleaseTag, fields, true, myReleaseTagErrorMessage, null);
    final String moduleName = myModuleName.getText().trim();
    if (moduleName.isEmpty()) {
      CvsFieldValidator.reportError(myModuleNameErrorMessage, CvsBundle.message("error.message.field.cannot.be.empty"), null);
      return false;
    }
    else {
      myModuleNameErrorMessage.setText(" ");
    }
    return result;
  }

  @Override
  public void activate() {
    final File selectedFile = mySelectImportLocationStep.getSelectedFile();
    if (!FileUtil.filesEqual(selectedFile, myDirectoryToImport)) {
      myDirectoryToImport = selectedFile;
      myModuleName.setText(myDirectoryToImport.getName());
      myModuleName.selectAll();
    }
  }

  private void selectAll() {
    myLogMessage.selectAll();
    myModuleName.selectAll();
    myReleaseTag.selectAll();
    myVendor.selectAll();
  }

  @Override
  protected JComponent createComponent() {
    return myPanel;
  }

  public String getVendor() {
    return myVendor.getText().trim();
  }

  public String getReleaseTag() {
    return myReleaseTag.getText().trim();
  }

  public String getLogMessage() {
    return myLogMessage.getText().trim();
  }

  public String getModuleName() {
    return myModuleName.getText().trim();
  }

  public Collection<FileExtension> getFileExtensions() {
    return myImportConfiguration.getExtensions();
  }
}