// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.export;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class ExportTestResultsForm {
  private JRadioButton myXmlRb;
  private JRadioButton myBundledTemplateRb;
  private TextFieldWithBrowseButton myCustomTemplateField;
  private TextFieldWithBrowseButton myFolderField;
  private JPanel myContentPane;
  private JLabel myOutputFolderLabel;
  private JRadioButton myCustomTemplateRb;
  private JTextField myFileNameField;
  private JLabel myMessageLabel;
  private JCheckBox myOpenExportedFileCb;

  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public ExportTestResultsForm(ExportTestResultsConfiguration config, String defaultFileName, @NlsSafe String defaultFolder) {
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateOnFormatChange();
      }
    };
    myXmlRb.addActionListener(listener);
    myBundledTemplateRb.addActionListener(listener);
    myCustomTemplateRb.addActionListener(listener);

    myOutputFolderLabel.setLabelFor(myFolderField.getChildComponent());

    myFileNameField.setText(defaultFileName);

    myCustomTemplateField.addBrowseFolderListener(ExecutionBundle.message("export.test.results.custom.template.chooser.title"), null, null,
                                                  new FileChooserDescriptor(true, false, false, false, false, false) {
                                 @Override
                                 public boolean isFileSelectable(@Nullable VirtualFile file) {
                                   return file != null &&
                                          ("xsl".equalsIgnoreCase(file.getExtension()) || "xslt".equalsIgnoreCase(file.getExtension()));
                                 }
                               }, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myFolderField.addBrowseFolderListener(ExecutionBundle.message("export.test.results.output.folder.chooser.title"), null, null,
                                          FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                          TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myFileNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateOpenInLabel();
      }
    });

    UserActivityWatcher watcher = new UserActivityWatcher();
    watcher.register(myContentPane);
    watcher.addUserActivityListener(new UserActivityListener() {
      @Override
      public void stateChanged() {
        myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
      }
    });

    myMessageLabel.setIcon(UIUtil.getBalloonWarningIcon());
    JRadioButton b;
    if (config.getExportFormat() == ExportTestResultsConfiguration.ExportFormat.Xml) {
      b = myXmlRb;
    }
    else if (config.getExportFormat() == ExportTestResultsConfiguration.ExportFormat.BundledTemplate) {
      b = myBundledTemplateRb;
    }
    else {
      b = myCustomTemplateRb;
    }
    b.setSelected(true);
    IdeFocusManager.findInstanceByComponent(myContentPane).requestFocus(b, true);
    myFolderField.setText(defaultFolder);
    myCustomTemplateField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(config.getUserTemplatePath())));
    myOpenExportedFileCb.setSelected(config.isOpenResults());
    updateOnFormatChange();
    updateOpenInLabel();
  }

  private void updateOpenInLabel() {
    myOpenExportedFileCb.setText(ExecutionBundle.message(
      shouldOpenInBrowser(myFileNameField.getText()) ? "export.test.results.open.browser" : "export.test.results.open.editor"));
  }

  public static boolean shouldOpenInBrowser(String filename) {
    return StringUtil.isNotEmpty(filename) && (filename.endsWith(".html") || filename.endsWith(".htm"));
  }

  private void updateOnFormatChange() {
    if (getExportFormat() == ExportTestResultsConfiguration.ExportFormat.UserTemplate) {
      myCustomTemplateField.setEnabled(true);
      IdeFocusManager.findInstanceByComponent(myContentPane).requestFocus(myCustomTemplateField.getChildComponent(), true);
    }
    else {
      myCustomTemplateField.setEnabled(false);
    }
    String filename = myFileNameField.getText();
    if (filename != null && filename.indexOf('.') != -1) {
      myFileNameField.setText(filename.substring(0, filename.lastIndexOf('.') + 1) + getExportFormat().getDefaultExtension());
    }
  }

  public void apply(ExportTestResultsConfiguration config) {
    config.setExportFormat(getExportFormat());
    config.setUserTemplatePath(FileUtil.toSystemIndependentName(myCustomTemplateField.getText()));
    config.setOutputFolder(FileUtil.toSystemIndependentName(myFolderField.getText()));
    config.setOpenResults(myOpenExportedFileCb.isSelected());
  }

  private ExportTestResultsConfiguration.ExportFormat getExportFormat() {
    if (myXmlRb.isSelected()) return ExportTestResultsConfiguration.ExportFormat.Xml;
    if (myBundledTemplateRb.isSelected()) return ExportTestResultsConfiguration.ExportFormat.BundledTemplate;
    return ExportTestResultsConfiguration.ExportFormat.UserTemplate;
  }

  public JComponent getContentPane() {
    return myContentPane;
  }

  public void addChangeListener(ChangeListener changeListener) {
    myEventDispatcher.addListener(changeListener);
  }

  @Nullable
  public @NlsContexts.Label String validate() {
    if (getExportFormat() == ExportTestResultsConfiguration.ExportFormat.UserTemplate) {
      if (StringUtil.isEmpty(myCustomTemplateField.getText())) {
        return ExecutionBundle.message("export.test.results.custom.template.path.empty");
      }
      File file = new File(myCustomTemplateField.getText());
      if (!file.isFile()) {
        return ExecutionBundle.message("export.test.results.custom.template.not.found", file.getAbsolutePath());
      }
    }

    if (StringUtil.isEmpty(myFileNameField.getText())) {
      return ExecutionBundle.message("export.test.results.output.filename.empty");
    }
    if (StringUtil.isEmpty(myFolderField.getText())) {
      return ExecutionBundle.message("export.test.results.output.path.empty");
    }

    return null;
  }

  public void showMessage(@Nullable @NlsContexts.Label String message) {
    myMessageLabel.setText(message);
    myMessageLabel.setVisible(message != null);
  }

  public JComponent getPreferredFocusedComponent() {
    return myFileNameField;
  }

  public String getFileName() {
    return myFileNameField.getText();
  }
}
