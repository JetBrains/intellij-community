// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ImportOldConfigsUsagesCollector.ImportOldConfigsState;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.function.Function;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @author max
 */
class ImportOldConfigsPanel extends JDialog {
  private JPanel myRootPanel;
  private JRadioButton myRbImportAuto;
  private JRadioButton myRbImport;
  private TextFieldWithBrowseButton myPrevInstallation;
  private JRadioButton myCustomButton;
  private JRadioButton myRbDoNotImport;
  private JButton myOkButton;

  private final File myGuessedOldConfig;
  private final Function<? super File, ? extends Pair<File, File>> myValidator;
  private final String myProductName;
  private VirtualFile myLastSelection = null;
  private Pair<File, File> myResult;

  ImportOldConfigsPanel(@Nullable File guessedOldConfig, Function<? super File, ? extends Pair<File, File>> validator) {
    super((Dialog)null, true);
    myGuessedOldConfig = guessedOldConfig;
    myValidator = validator;
    myProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    setTitle(ApplicationBundle.message("title.import.settings", myProductName));
    init();
  }

  private void init() {
    MnemonicHelper.init(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbImportAuto);
    group.add(myRbImport);
    group.add(myRbDoNotImport);
    myRbDoNotImport.setSelected(true);

    myRbDoNotImport.setText(ApplicationBundle.message("radio.do.not.import"));
    if (myGuessedOldConfig != null) {
      String path = FileUtil.getLocationRelativeToUserHome(myGuessedOldConfig.getAbsolutePath());
      myRbImportAuto.setText(ApplicationBundle.message("radio.import.auto", path));
      myRbImportAuto.setSelected(true);
    }
    else {
      myRbImportAuto.setVisible(false);
    }

    myRbImport.addChangeListener(e -> update());

    if (SystemInfo.isMac) {
      myLastSelection = new CoreLocalFileSystem().findFileByPath("/Applications");
    }
    else if (SystemInfo.isWindows) {
      String programFiles = System.getenv("ProgramFiles");
      if (programFiles != null) {
        File jetBrainsHome = new File(programFiles, "JetBrains");
        if (jetBrainsHome.isDirectory()) {
          myLastSelection = new CoreLocalVirtualFile(new CoreLocalFileSystem(), jetBrainsHome);
        }
      }
    }
    myPrevInstallation.setTextFieldPreferredWidth(50);
    myPrevInstallation.addActionListener(e -> {
      FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
      chooserDescriptor.setHideIgnored(false);
      chooserDescriptor.withFileFilter(file -> file.isDirectory() || ConfigImportHelper.isSettingsFile(file));
      Ref<VirtualFile> fileRef = Ref.create();
      PathChooserDialog chooser = FileChooserFactoryImpl.createNativePathChooserIfEnabled(chooserDescriptor, null, myRootPanel);
      if (chooser == null) {
        File lastSelectedFile = myLastSelection == null ? null : VfsUtilCore.virtualToIoFile(myLastSelection);
        JFileChooser fc = new JFileChooser(lastSelectedFile == null ? null : lastSelectedFile.getParentFile());
        fc.setSelectedFile(lastSelectedFile);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileHidingEnabled(SystemInfo.isWindows || SystemInfo.isMac);
        fc.setFileFilter(new FileNameExtensionFilter("settings file", "zip", "jar"));
        int returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          if (file != null) {
            fileRef.set(new CoreLocalVirtualFile(new CoreLocalFileSystem(), file));
            myPrevInstallation.setText(file.getAbsolutePath());
          }
        }
      }
      else {
        chooser.choose(myLastSelection, files -> fileRef.set(files.get(0)));
      }

      if (!fileRef.isNull()) {
        File file = VfsUtilCore.virtualToIoFile(fileRef.get());
        myLastSelection = fileRef.get();
        myPrevInstallation.setText(file.getAbsolutePath());
      }
    });

    myOkButton.addActionListener(e -> close());

    CloudConfigProvider configProvider = CloudConfigProvider.getProvider();
    if (configProvider != null) {
      configProvider.initConfigsPanel(group, myCustomButton);
    }

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myRootPanel);
    getRootPane().setDefaultButton(myOkButton);

    update();
    pack();
    setLocationRelativeTo(null);
    setMinimumSize(getSize());
  }

  private void update() {
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  private void close() {
    ImportOldConfigsState.getInstance().saveImportOldConfigType(myRbImportAuto, myRbImport, myRbDoNotImport);

    if (myRbImport.isSelected()) {
      String text = myPrevInstallation.getText();
      if (StringUtil.isEmptyOrSpaces(text)) {
        showError(ApplicationBundle.message("error.please.select.previous.installation.home", myProductName));
        return;
      }

      File selectedDir = new File(FileUtil.toCanonicalPath(text.trim()));

      if (selectedDir.isFile()) {
        if (!ConfigImportHelper.isValidSettingsFile(selectedDir)) {
          showError(IdeBundle.message("error.file.contains.no.settings.to.import", selectedDir, IdeBundle.message("message.please.ensure.correct.settings")));
          return;
        }
        myResult = pair(selectedDir, null);
      }
      else {
        if (FileUtil.pathsEqual(selectedDir.getPath(), PathManager.getHomePath()) ||
            FileUtil.pathsEqual(selectedDir.getPath(), PathManager.getConfigPath())) {
          showError(ApplicationBundle.message("error.selected.current.installation.home", myProductName));
          return;
        }

        Pair<File, File> result = myValidator.apply(selectedDir);
        if (result == null) {
          showError(ApplicationBundle.message("error.does.not.appear.to.be.installation.home", selectedDir, myProductName));
          return;
        }

        if (!result.first.canRead()) {
          showError(ApplicationBundle.message("error.no.read.permissions", result));
          return;
        }

        myResult = result;
      }
    }

    dispose();
  }

  private void showError(String message) {
    String title = ApplicationBundle.message("title.installation.home.required");
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  @Nullable Pair<File, File> getSelectedFile() {
    if (myRbImportAuto.isSelected()) return pair(myGuessedOldConfig, null);
    if (myRbImport.isSelected()) return myResult;
    return null;
  }
}