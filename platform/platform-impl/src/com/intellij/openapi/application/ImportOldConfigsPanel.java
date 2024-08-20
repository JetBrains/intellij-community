// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.ide.BootstrapBundle;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;

final class ImportOldConfigsPanel extends JDialog {
  private JPanel myRootPanel;
  private JRadioButton myRbImportAuto;
  private JRadioButton myRbImport;
  private TextFieldWithBrowseButton myPrevInstallation;
  private JRadioButton myCustomButton;
  private JRadioButton myRbDoNotImport;
  private JButton myOkButton;
  private ComboBox<Path> myComboBoxOldPaths;

  private final List<Path> myGuessedOldConfigDirs;
  private final Function<? super Path, ? extends Pair<Path, Path>> myValidator;
  private final String myProductName;
  private Path myLastSelection = null;
  private Pair<Path, Path> myResult;

  ImportOldConfigsPanel(List<Path> guessedOldConfigDirs, Function<? super Path, ? extends Pair<Path, Path>> validator) {
    super((Dialog)null, true);

    ComponentUtil.decorateWindowHeader(rootPane);

    myGuessedOldConfigDirs = guessedOldConfigDirs;
    myValidator = validator;
    myProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    setTitle(BootstrapBundle.message("import.settings.title", myProductName));
    init();
  }

  private void init() {
    MnemonicHelper.init(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbImportAuto);
    group.add(myRbImport);
    group.add(myRbDoNotImport);
    myRbDoNotImport.setSelected(true);

    if (myGuessedOldConfigDirs.isEmpty()) {
      myRbImportAuto.setVisible(false);
      myComboBoxOldPaths.setVisible(false);
    }
    else {
      myComboBoxOldPaths.setModel(new CollectionComboBoxModel<>(myGuessedOldConfigDirs));
      myComboBoxOldPaths.setSelectedItem(myGuessedOldConfigDirs.get(0));
      myRbImportAuto.setSelected(true);
    }
    for (Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements(); ) {
      e.nextElement().addChangeListener(event -> update());
    }
    if (SystemInfo.isMac) {
      myLastSelection = Paths.get("/Applications");
    }
    else if (SystemInfo.isWindows) {
      String programFiles = System.getenv("ProgramFiles");
      if (programFiles != null) {
        Path candidate = Paths.get(programFiles, "JetBrains");
        myLastSelection = Files.isDirectory(candidate) ? candidate : Paths.get(programFiles);
      }
    }
    myPrevInstallation.setTextFieldPreferredWidth(50);
    myPrevInstallation.addActionListener(e -> {
      FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
      chooserDescriptor.setHideIgnored(false);
      chooserDescriptor.withFileFilter(file -> file.isDirectory() || ConfigImportHelper.isSettingsFile(file));
      Ref<File> fileRef = Ref.create();
      PathChooserDialog chooser = FileChooserFactoryImpl.createNativePathChooserIfEnabled(chooserDescriptor, null, myRootPanel);
      if (chooser != null) {
        VirtualFile vf = myLastSelection != null ? new CoreLocalVirtualFile(new CoreLocalFileSystem(), myLastSelection) : null;
        chooser.choose(vf, files -> fileRef.set(new File(files.get(0).getPresentableUrl())));
      }
      else {
        JFileChooser fc = new JFileChooser(myLastSelection != null ? myLastSelection.getParent().toFile() : null);
        fc.setSelectedFile(myLastSelection != null ? myLastSelection.toFile() : null);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileHidingEnabled(SystemInfo.isWindows || SystemInfo.isMac);
        fc.setFileFilter(new FileNameExtensionFilter("settings file", "zip", "jar"));
        @SuppressWarnings("DuplicatedCode")
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          if (file != null) {
            fileRef.set(file);
            myPrevInstallation.setText(file.getAbsolutePath());
          }
        }
      }
      if (!fileRef.isNull()) {
        myLastSelection = fileRef.get().toPath();
        myPrevInstallation.setText(fileRef.get().getAbsolutePath());
      }
    });

    myOkButton.addActionListener(e -> close());

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myRootPanel);
    getRootPane().setDefaultButton(myOkButton);

    update();
    pack();
    setLocationRelativeTo(null);
    setMinimumSize(getSize());
  }

  private void update() {
    myComboBoxOldPaths.setEnabled(myRbImportAuto.isSelected());
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  private void close() {
    if (myRbImport.isSelected()) {
      String text = myPrevInstallation.getText();
      if (StringUtil.isEmptyOrSpaces(text)) {
        showError(BootstrapBundle.message("import.chooser.error.empty", myProductName));
        return;
      }

      Path selectedDir = Paths.get(FileUtil.toCanonicalPath(text.trim()));

      if (Files.isRegularFile(selectedDir)) {
        if (!ConfigImportHelper.isValidSettingsFile(selectedDir.toFile())) {
          showError(BootstrapBundle.message("import.chooser.error.invalid", selectedDir));
          return;
        }
        myResult = new Pair<>(selectedDir, null);
      }
      else {
        if (FileUtil.pathsEqual(selectedDir.toString(), PathManager.getHomePath()) ||
            FileUtil.pathsEqual(selectedDir.toString(), PathManager.getConfigPath())) {
          showError(BootstrapBundle.message("import.chooser.error.current", myProductName));
          return;
        }

        Pair<Path, Path> result = myValidator.apply(selectedDir);
        if (result == null) {
          showError(BootstrapBundle.message("import.chooser.error.unrecognized", selectedDir, myProductName));
          return;
        }

        myResult = result;
      }
    }

    dispose();
  }

  private void showError(@NlsContexts.DialogMessage String message) {
    String title = BootstrapBundle.message("import.chooser.error.title");
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  @Nullable Pair<Path, Path> getSelectedFile() {
    ImportOldConfigsState.Companion.getInstance().saveImportOldConfigType(myRbImportAuto, myRbImport, myRbDoNotImport, myResult != null);

    if (myRbImportAuto.isSelected()) {
      return new Pair<>(myGuessedOldConfigDirs.get(Math.max(myComboBoxOldPaths.getSelectedIndex(), 0)), null);
    }
    if (myRbImport.isSelected()) {
      return myResult;
    }
    return null;
  }
}
