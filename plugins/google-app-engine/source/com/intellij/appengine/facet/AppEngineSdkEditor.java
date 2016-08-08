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
package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
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
    myPathEditor = new ComboboxWithBrowseButton(new ComboBox(100));
    myPathEditor.addBrowseFolderListener(project, new ComponentWithBrowseButton.BrowseFolderActionListener<>("Google App Engine SDK",
                                                                                                             "Specify Google App Engine Java SDK home",
                                                                                                             myPathEditor, project,
                                                                                                             FileChooserDescriptorFactory
                                                                                                               .createSingleFolderDescriptor(),
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
