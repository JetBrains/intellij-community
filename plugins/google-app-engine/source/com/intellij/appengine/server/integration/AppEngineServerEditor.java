package com.intellij.appengine.server.integration;

import com.intellij.javaee.appServerIntegrations.ApplicationServerPersistentDataEditor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineServerEditor extends ApplicationServerPersistentDataEditor<AppEngineServerData> {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton mySdkHomeField;

  public AppEngineServerEditor() {
    mySdkHomeField.addBrowseFolderListener("Google App Engine SDK", "Specify Google App Engine Java SDK home", null, FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  protected void resetEditorFrom(AppEngineServerData s) {
    mySdkHomeField.setText(FileUtil.toSystemDependentName(s.getSdkPath()));
  }

  protected void applyEditorTo(AppEngineServerData s) {
    s.setSdkPath(FileUtil.toSystemIndependentName(mySdkHomeField.getText()));
  }

  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }

  protected void disposeEditor() {
  }
}
