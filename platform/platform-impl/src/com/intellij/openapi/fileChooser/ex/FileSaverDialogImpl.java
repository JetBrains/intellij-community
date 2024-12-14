// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class FileSaverDialogImpl extends FileChooserDialogImpl implements FileSaverDialog {
  protected final JTextField myFileName = new JTextField(20);
  protected final JComboBox<String> myExtensions = new ComboBox<>();
  protected final FileSaverDescriptor myDescriptor;

  public FileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    super(descriptor, parent);
    myDescriptor = descriptor;
    fillExtensions(descriptor);
    setTitle(getChooserTitle(descriptor));
  }

  public FileSaverDialogImpl(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    super(descriptor, project);
    myDescriptor = descriptor;
    fillExtensions(descriptor);
    setTitle(getChooserTitle(descriptor));
  }

  private void fillExtensions(FileSaverDescriptor descriptor) {
    var extFilter = descriptor.getExtensionFilter();
    if (extFilter != null) {
      extFilter.second.forEach(ext -> myExtensions.addItem(ext));
    }
  }

  private static @NlsContexts.DialogTitle String getChooserTitle(final FileSaverDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.save.dialog.default.title");
  }

  @Override
  public @Nullable VirtualFileWrapper save(@Nullable Path baseDir, @Nullable String filename) {
    return save(baseDir == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir), filename);
  }

  @Override
  protected void init() {
    super.init();

    // ensure that in the tree only the folder is selected once the user starts editing the file field
    myFileName.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        changed();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        changed();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        changed();
      }

      private void changed() {
        // when the file name changes AND has focus, then this is the user editing and not an automatic update after selecting a file.
        if (myFileName.hasFocus()) {
          // if the selected item is a file (not a directory), change the selection to the parent folder.
          // this is the symmetric logic as in FileSaverDialogImpl.getFile()
          if (myFileSystemTree.getSelectedFile() != null && !myFileSystemTree.getSelectedFile().isDirectory()) {
            // now switch to parent folder of the file
            myFileSystemTree.select(myFileSystemTree.getSelectedFile().getParent(), () -> {
            });
          }
        }
      }
    });
  }

  @Override
  public @Nullable VirtualFileWrapper save(@Nullable VirtualFile baseDir, @Nullable String filename) {
    init();
    restoreSelection(baseDir);
    myFileSystemTree.addListener(new FileSystemTree.Listener() {
      @Override
      public void selectionChanged(final @NotNull List<? extends VirtualFile> selection) {
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

  protected @Nullable File getFile() {
    VirtualFile selected = myFileSystemTree.getSelectedFile();
    if (selected != null && !selected.isDirectory()) {
      return new File(selected.getPath());
    }

    String path = (selected == null) ? myPathTextField.getTextFieldText() : selected.getPath();
    File dir = new File(path);
    if (!dir.exists()) {
      return null;
    }
    if (dir.isDirectory()) {
      String child = myFileName.getText();
      path = OSAgnosticPathUtil.isAbsolute(child) ? child : FileUtil.join(path, child);
    }

    boolean correctExt = true;
    var extFilter = myDescriptor.getExtensionFilter();
    if (extFilter != null) {
      var _path = path;
      correctExt = extFilter.second.stream().anyMatch(ext -> Strings.endsWithIgnoreCase(_path, '.' + ext));
    }

    if (!correctExt) {
      Object obj = myExtensions.getSelectedItem();
      String selectedExtension = obj == null ? null : obj.toString();
      if (!Strings.isEmpty(selectedExtension)) {
        path += '.' + selectedExtension;
      }
    }

    return new File(path);
  }

  private void updateFileName(List<? extends VirtualFile> selection) {
    for (VirtualFile file : selection) {
      if (file.isDirectory()) {
        myPathTextField.getField().setText(VfsUtil.getReadableUrl(file));
      }
      else {
        myFileName.setText(file.getName());
        final VirtualFile parent = file.getParent();
        if (parent != null) {
          myPathTextField.getField().setText(VfsUtil.getReadableUrl(parent));
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
      protected void textChanged(@NotNull DocumentEvent e) {
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
    return path != null && new File(path.trim()).exists() && !myFileName.getText().trim().isEmpty();
  }

  protected void updateOkButton() {
    setOKActionEnabled(true);
  }

  @Override
  public void setOKActionEnabled(boolean isEnabled) {
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
