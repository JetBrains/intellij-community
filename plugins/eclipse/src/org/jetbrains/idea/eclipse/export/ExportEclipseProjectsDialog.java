/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.export;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ExportEclipseProjectsDialog extends DialogWrapper {
  private JPanel contentPane;
  private ElementsChooser<Module> moduleChooser;
  private JCheckBox linkCheckBox;
  private TextFieldWithBrowseButton myUserLibrariesTF;
  private JCheckBox myExportProjectLibrariesCb;
  private JLabel myPathToUserLibsLabel;

  public ExportEclipseProjectsDialog(final Project project, List<Module> modules) {
    super(project, false);
    moduleChooser.setElements(modules, true);
    setTitle(EclipseBundle.message("eclipse.export.dialog.title"));
    init();
    myUserLibrariesTF.setText(project.getBaseDir().getPath() + File.separator + project.getName() + ".userlibraries");
    myUserLibrariesTF.addBrowseFolderListener("Locate .userlibraries", "Locate .userlibraries file where project libraries would be exported", project, FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myExportProjectLibrariesCb.setSelected(true);
    myExportProjectLibrariesCb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myUserLibrariesTF.setEnabled(myExportProjectLibrariesCb.isSelected());
        myPathToUserLibsLabel.setEnabled(myExportProjectLibrariesCb.isSelected());
      }
    });
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  private void createUIComponents() {
    moduleChooser = new ElementsChooser<Module>(true) {
      protected String getItemText(@NotNull final Module module) {
        return module.getName();
      }
    };
  }

  public boolean isLink() {
    return linkCheckBox.isSelected();
  }

  public List<Module> getSelectedModules() {
    return moduleChooser.getMarkedElements();
  }

  @Nullable
  public File getUserLibrariesFile() {
    return myExportProjectLibrariesCb.isSelected() ? new File(myUserLibrariesTF.getText()) : null;
  }
}
