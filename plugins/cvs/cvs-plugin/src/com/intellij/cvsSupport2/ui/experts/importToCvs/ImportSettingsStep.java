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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

  private boolean myIsInitialized = false;
  private File myDirectoryToImport;

  private final SelectImportLocationStep mySelectImportLocationStep;
  private final ImportConfiguration myImportConfiguration;

  private JCheckBox myCheckoutAfterImport;
  private JCheckBox myMakeCheckedOutFilesReadOnly;
  private JLabel myVendorErrorMessage;
  private JLabel myReleaseTagErrorMessage;
  private JLabel myNameLabel;
  private JLabel myVendorLabel;
  private JLabel myReleaseTagLabel;
  private JLabel myLogMessageLabel;
  private JButton myKeywordExpansionButton;

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

    checkFields();

    final MyDocumentListener listener = new MyDocumentListener();
    myVendor.getDocument().addDocumentListener(listener);
    myReleaseTag.getDocument().addDocumentListener(listener);

    myLogMessageLabel.setLabelFor(myLogMessage);
    myNameLabel.setLabelFor(myModuleName);
    myReleaseTagLabel.setLabelFor(myReleaseTag);
    myVendorLabel.setLabelFor(myVendor);

    myLogMessage.setWrapStyleWord(true);
    myLogMessage.setLineWrap(true);

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

  private boolean isValidInput() {
    final JTextField[] fields = new JTextField[]{myReleaseTag, myVendor};
    boolean result = CvsFieldValidator.checkField(myVendor, fields, true, myVendorErrorMessage, null);
    result &= CvsFieldValidator.checkField(myReleaseTag, fields, true, myReleaseTagErrorMessage, null);
    return result;
  }

  private void checkFields() {
    final CvsWizard wizard = getWizard();
    if (!isValidInput()) {
      wizard.disableNextAndFinish();
    }
    else {
      wizard.enableNextAndFinish();
    }
  }

  @Override
  public boolean nextIsEnabled() {
    return isValidInput();
  }

  @Override
  public boolean setActive() {
    if (!myIsInitialized) {
      myIsInitialized = true;
      myReleaseTag.setText(myImportConfiguration.RELEASE_TAG);
      myVendor.setText(myImportConfiguration.VENDOR);
      myLogMessage.setText(myImportConfiguration.LOG_MESSAGE);
      myCheckoutAfterImport.setSelected(myImportConfiguration.CHECKOUT_AFTER_IMPORT);
      myMakeCheckedOutFilesReadOnly.setSelected(myImportConfiguration.MAKE_NEW_FILES_READ_ONLY);
      updateCheckoutSettingsVisibility();
      selectAll();
      myModuleName.selectAll();
      myModuleName.requestFocus();
    }

    final File selectedFile = mySelectImportLocationStep.getSelectedFile();
    if (selectedFile != null && !selectedFile.equals(myDirectoryToImport)) {
      myDirectoryToImport = selectedFile;
      myModuleName.setText(myDirectoryToImport.getName());
      myModuleName.selectAll();
    }
    return true;
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

  private class MyDocumentListener implements DocumentListener {

    public MyDocumentListener() {}

    @Override
    public void changedUpdate(DocumentEvent e) {
      checkFields();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      checkFields();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      checkFields();
    }
  }
}
