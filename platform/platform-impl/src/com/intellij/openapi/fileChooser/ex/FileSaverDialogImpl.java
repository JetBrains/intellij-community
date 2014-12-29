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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FileSaverDialogImpl extends FileChooserDialogImpl implements FileSaverDialog {
  protected final JTextField myFileName = new JTextField(20);
  protected final JComboBox myExtensions = new JComboBox();
  protected final FileSaverDescriptor myDescriptor;

  public FileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    super(descriptor, parent);
    myDescriptor = descriptor;
    for (String ext : descriptor.getFileExtensions()) {
      myExtensions.addItem(ext);
    }
    setTitle(getChooserTitle(descriptor));
  }

  public FileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    super(descriptor, project);
    myDescriptor = descriptor;
    for (String ext : descriptor.getFileExtensions()) {
      myExtensions.addItem(ext);
    }
    setTitle(getChooserTitle(descriptor));
  }

  private static String getChooserTitle(final FileSaverDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.save.dialog.default.title");
  }

  @Override
  @Nullable
  public VirtualFileWrapper save(@Nullable VirtualFile baseDir, @Nullable final String filename) {
    init();
    restoreSelection(baseDir);
    myFileSystemTree.addListener(new FileSystemTree.Listener() {
      @Override
      public void selectionChanged(final List<VirtualFile> selection) {
        updateFileName(selection);
        updateOkButton();
      }
    }, myDisposable);

    if (filename != null) {
      myFileName.setText(filename);
    }

    show();

    if (getExitCode() == OK_EXIT_CODE) {
      final File file = getFile();
      return file == null ? null : new VirtualFileWrapper(file);
    }
    return null;
  }

  @Nullable
  protected File getFile() {
    final VirtualFile selected = myFileSystemTree.getSelectedFile();
    if (selected != null && !selected.isDirectory()) {
      return new File(selected.getPath());
    }

    String path = (selected == null) ? myPathTextField.getTextFieldText() : selected.getPath();
    final File dir = new File(path);
    if (!dir.exists()) {
      return null;
    }
    if (dir.isDirectory()) {
      path += File.separator + myFileName.getText();
    }

    boolean correctExt = true;
    for (String ext : myDescriptor.getFileExtensions()) {
      correctExt = path.endsWith("." + ext);
      if (correctExt) break;
    }

    if (!correctExt) {
      path += "." + myExtensions.getSelectedItem();
    }

    return new File(path);
  }

  private void updateFileName(List<VirtualFile> selection) {
    for (VirtualFile file : selection) {
      if (file.isDirectory()) {
        myPathTextField.getField().setText(file.getPath());
      } else {
        myFileName.setText(file.getName());
        final VirtualFile parent = file.getParent();
        if (parent != null) {
          myPathTextField.getField().setText(parent.getPath());
        }
      }
    }
    updateOkButton();
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent component =  super.createCenterPanel();
    MyPanel panel = new MyPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    panel.add(component, BorderLayout.CENTER);
    panel.add(createFileNamePanel(), BorderLayout.SOUTH);
    return panel;
  }

  protected JComponent createFileNamePanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(UIBundle.message("file.chooser.save.dialog.file.name")), BorderLayout.WEST);
    myFileName.setText("");
    myFileName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateOkButton();
      }
    });

    panel.add(myFileName, BorderLayout.CENTER);
    if (myExtensions.getModel().getSize() > 0) {
      myExtensions.setSelectedIndex(0);
      panel.add(myExtensions, BorderLayout.EAST);
    }
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileName;
  }

  private boolean isFileNameExist() {
    if (myPathTextField == null) return false;
    final String path = myPathTextField.getTextFieldText();
    return path != null && new File(path.trim()).exists() && myFileName.getText().trim().length() > 0;
  }

  protected void updateOkButton() {
    setOKActionEnabled(true);
  }

  @Override
  protected void setOKActionEnabled(boolean isEnabled) {
    //double check. FileChooserFactoryImpl sets enable ok button 
    super.setOKActionEnabled(isFileNameExist());
  }

  @Override
  protected void doOKAction() {
    final File file = getFile();
    if (file != null && file.exists()) {
      if (Messages.YES != Messages.showYesNoDialog(getRootPane(),
                                                  UIBundle.message("file.chooser.save.dialog.confirmation", file.getName()),
                                                  UIBundle.message("file.chooser.save.dialog.confirmation.title"),
                                                  Messages.getWarningIcon())) {
        return;
      }
    }
    storeSelection(myFileSystemTree.getSelectedFile());
    super.doOKAction();
  }
}
