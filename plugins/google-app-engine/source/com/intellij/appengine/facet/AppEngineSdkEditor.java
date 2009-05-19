package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.sdk.impl.AppEngineSdkImpl;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineSdkEditor {
  private ComboboxWithBrowseButton myPathEditor;

  public AppEngineSdkEditor(final @Nullable Project project, boolean checkSdk) {
    myPathEditor = new ComboboxWithBrowseButton();
    myPathEditor.addBrowseFolderListener(project, new AppEngineFolderActionListener(project, myPathEditor, checkSdk));
    final JComboBox comboBox = myPathEditor.getComboBox();
    comboBox.setEditable(true);
    comboBox.removeAllItems();
    for (AppEngineSdk sdk : AppEngineSdkManager.getInstance().getValidSdks()) {
      comboBox.addItem(FileUtil.toSystemDependentName(sdk.getSdkHomePath()));
    }
  }

  public ComboboxWithBrowseButton getMainComponent() {
    return myPathEditor;
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName((String)myPathEditor.getComboBox().getEditor().getItem());
  }

  public void setPath(final String path) {
    myPathEditor.getComboBox().setSelectedItem(FileUtil.toSystemDependentName(path));
  }

  public void setDefaultPath() {
    final JComboBox comboBox = myPathEditor.getComboBox();
    if (comboBox.getItemCount() > 0) {
      comboBox.setSelectedIndex(0);
    }
  }

  public JComboBox getComboBox() {
    return myPathEditor.getComboBox();
  }

  public static class AppEngineFolderActionListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JComboBox> {
    private final ComboboxWithBrowseButton myPathEditor;
    private final boolean myCheckSdk;

    public AppEngineFolderActionListener(@Nullable Project project, final ComboboxWithBrowseButton pathEditor, boolean checkSdk) {
      super("Google App Engine SDK", "Specify Google App Engine Java SDK home", pathEditor, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(), TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
      myPathEditor = pathEditor;
      myCheckSdk = checkSdk;
    }

    @Override
    protected void onFileChoosen(VirtualFile chosenFile) {
      super.onFileChoosen(chosenFile);
      if (myCheckSdk) {
        final ValidationResult result = AppEngineSdkImpl.checkPath(chosenFile.getPath());
        if (!result.isOk()) {
          Messages.showErrorDialog(myPathEditor, result.getErrorMessage());
        }
      }
    }
  }
}
