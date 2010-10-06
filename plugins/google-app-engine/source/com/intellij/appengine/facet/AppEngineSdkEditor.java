package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineSdkEditor {
  private ComboboxWithBrowseButton myPathEditor;

  public AppEngineSdkEditor(final @Nullable Project project) {
    myPathEditor = new ComboboxWithBrowseButton();
    final ComboboxWithBrowseButton pathEditor = myPathEditor;
    myPathEditor.addBrowseFolderListener(project, new ComponentWithBrowseButton.BrowseFolderActionListener<JComboBox>("Google App Engine SDK",
                                     "Specify Google App Engine Java SDK home",
                                     pathEditor, project,
                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT));
    final JComboBox comboBox = myPathEditor.getComboBox();
    comboBox.setEditable(true);
    comboBox.removeAllItems();
    for (AppEngineSdk sdk : AppEngineSdkManager.getInstance().getValidSdks()) {
      comboBox.addItem(FileUtil.toSystemDependentName(sdk.getSdkHomePath()));
    }
  }


  public JPanel getMainComponent() {
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
}
