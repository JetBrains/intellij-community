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
package org.jetbrains.android.newProject;

import com.intellij.CommonBundle;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkComboBoxWithBrowseButton extends ComboboxWithBrowseButton {
  public AndroidSdkComboBoxWithBrowseButton() {
    final JComboBox sdkCombobox = getComboBox();

    sdkCombobox.setRenderer(new ListCellRendererWrapper(sdkCombobox.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Sdk) {
          setText(((Sdk)value).getName());
          setIcon(((Sdk)value).getSdkType().getIcon());
        }
        else {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ProjectJdksEditor editor =
          new ProjectJdksEditor(null, ProjectManager.getInstance().getDefaultProject(), AndroidSdkComboBoxWithBrowseButton.this);
        editor.show();
        if (editor.isOK()) {
          final Sdk selectedJdk = editor.getSelectedJdk();
          rebuildSdksListAndSelectSdk(selectedJdk);
          if (selectedJdk == null || !(selectedJdk.getSdkType().equals(AndroidSdkType.getInstance()))) {
            Messages.showErrorDialog(AndroidSdkComboBoxWithBrowseButton.this, AndroidBundle.message("select.platform.error"),
                                     CommonBundle.getErrorTitle());
          }
        }
      }
    });
    getButton().setToolTipText(AndroidBundle.message("android.add.sdk.tooltip"));
  }

  public Sdk getSelectedSdk() {
    return (Sdk)getComboBox().getSelectedItem();
  }

  public void rebuildSdksListAndSelectSdk(final Sdk selectedSdk) {
    final List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());

    final JComboBox sdksComboBox = getComboBox();
    sdksComboBox.setModel(new DefaultComboBoxModel(sdks.toArray()));

    if (selectedSdk != null) {
      for (Sdk candidateSdk : sdks) {
        if (candidateSdk != null && candidateSdk.getName().equals(selectedSdk.getName())) {
          sdksComboBox.setSelectedItem(candidateSdk);
          return;
        }
      }
    }
    sdksComboBox.setSelectedItem(null);
  }
}
