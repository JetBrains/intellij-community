// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

public class ExportEclipseProjectsDialog extends DialogWrapper {
  private JPanel contentPane;
  private ElementsChooser<Module> moduleChooser;
  private JCheckBox linkCheckBox;
  private TextFieldWithBrowseButton myUserLibrariesTF;
  private JCheckBox myExportProjectLibrariesCb;
  private JLabel myPathToUserLibsLabel;

  public ExportEclipseProjectsDialog(final Project project, List<? extends Module> modules) {
    super(project, false);
    moduleChooser.setElements(modules, true);
    setTitle(EclipseBundle.message("eclipse.export.dialog.title"));
    init();
    myUserLibrariesTF.setText(project.getBasePath() + File.separator + project.getName() + ".userlibraries");
    myUserLibrariesTF.addBrowseFolderListener(EclipseBundle.message("button.browse.dialog.title.locate.user.libraries"), EclipseBundle
      .message("button.browse.dialog.description.locate.user.libraries.file"), project, FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myExportProjectLibrariesCb.setSelected(true);
    myExportProjectLibrariesCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myUserLibrariesTF.setEnabled(myExportProjectLibrariesCb.isSelected());
        myPathToUserLibsLabel.setEnabled(myExportProjectLibrariesCb.isSelected());
      }
    });
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  private void createUIComponents() {
    moduleChooser = new ElementsChooser<>(true) {
      @Override
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
