// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.internal.statistic.customUsageCollectors.ideSettings.IdeInitialConfigButtonUsages;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author max
 */
public class ImportOldConfigsPanel extends JDialog {
  private TextFieldWithBrowseButton myPrevInstallation;
  private JRadioButton myRbDoNotImport;
  private JRadioButton myRbImport;
  private JPanel myRootPanel;
  private File myLastSelection = null;
  private JButton myOkButton;
  private JLabel mySuggestLabel;
  private JRadioButton myRbImportAuto;
  private JRadioButton myCustomButton;

  private final File myGuessedOldConfig;
  private final ConfigImportSettings mySettings;

  public ImportOldConfigsPanel(@Nullable File guessedOldConfig, ConfigImportSettings settings) {
    super((Dialog)null, true);
    myGuessedOldConfig = guessedOldConfig;
    mySettings = settings;
    init();
  }

  private void init() {
    MnemonicHelper.init(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbDoNotImport);
    group.add(myRbImport);
    group.add(myRbImportAuto);
    myRbDoNotImport.setSelected(true);

    String productName = mySettings.getProductName(ThreeState.UNSURE);
    mySuggestLabel.setText(mySettings.getTitleLabel(productName));
    myRbDoNotImport.setText(mySettings.getDoNotImportLabel(productName));
    if (myGuessedOldConfig != null) {
      myRbImportAuto.setText(mySettings.getAutoImportLabel(myGuessedOldConfig));
      myRbImportAuto.setSelected(true);
    }
    else {
      myRbImportAuto.setVisible(false);
    }

    myRbImport.addChangeListener(e -> update());

    if (myGuessedOldConfig != null) {
      myPrevInstallation.setText(myGuessedOldConfig.getParent());
    }
    else if (SystemInfo.isMac) {
      myPrevInstallation.setText(findPreviousInstallationMac(productName));
    }
    else if (SystemInfo.isWindows) {
      String prevInstall = findPreviousInstallationWindows(productName);
      if (prevInstall != null) {
        myPrevInstallation.setText(prevInstall);
      }
    }

    myPrevInstallation.addActionListener(e -> {
      JFileChooser fc = myLastSelection != null ? new JFileChooser(myLastSelection) : new JFileChooser();

      fc.setFileSelectionMode(SystemInfo.isMac ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);
      fc.setFileHidingEnabled(!SystemInfo.isLinux);

      int returnVal = fc.showOpenDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        File file = fc.getSelectedFile();
        if (file != null) {
          myLastSelection = file;
          myPrevInstallation.setText(file.getAbsolutePath());
        }
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

    setTitle(ApplicationBundle.message("title.complete.installation"));

    update();

    pack();
    setLocationRelativeTo(null);
  }

  private void update() {
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  @Nullable
  private static String findPreviousInstallationWindows(String productName) {
    String programFiles = System.getenv("ProgramFiles");
    if (programFiles != null) {
      File jetbrainsHome = new File(programFiles, "JetBrains");
      File[] files = jetbrainsHome.isDirectory() ? jetbrainsHome.listFiles() : null;
      if (files != null) {
        String latestVersion = null;
        File latestFile = null;
        for (File file : files) {
          if (file.isDirectory() && file.getName().startsWith(productName)) {
            String versionName = file.getName().substring(productName.length()).trim();
            if (latestVersion == null || StringUtil.compareVersionNumbers(latestVersion, versionName) > 0) {
              latestVersion = versionName;
              latestFile = file;
            }
          }
        }
        if (latestFile != null) {
          return latestFile.getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static String findPreviousInstallationMac(String productName) {
    String mostProbable = "/Applications/" + productName;
    return new File(mostProbable).exists() ? mostProbable : "/Applications";
  }

  private void close() {
    IdeInitialConfigButtonUsages.setConfigImport(myRbDoNotImport, myRbImport, myRbImportAuto, myCustomButton);

    if (myRbImport.isSelected()) {
      String instHome = FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(myPrevInstallation.getText()));

      String productWithVendor = mySettings.getProductName(ThreeState.YES);
      if (StringUtil.isEmptyOrSpaces(instHome)) {
        showError(mySettings.getEmptyHomeErrorText(productWithVendor));
        return;
      }

      String thisInstanceHome = PathManager.getHomePath();
      if (SystemInfo.isFileSystemCaseSensitive ? thisInstanceHome.equals(instHome) : thisInstanceHome.equalsIgnoreCase(instHome)) {
        showError(mySettings.getCurrentHomeErrorText(productWithVendor));
        return;
      }

      if (myRbImport.isSelected() && !ConfigImportHelper.isInstallationHomeOrConfig(instHome, mySettings)) {
        showError(mySettings.getInvalidHomeErrorText(productWithVendor, instHome));
        return;
      }

      if (!new File(instHome).canRead()) {
        showError(mySettings.getInaccessibleHomeErrorText(instHome));
        return;
      }
    }

    //noinspection SSBasedInspection
    dispose();
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, mySettings.getInstallationHomeRequiredTitle(), JOptionPane.ERROR_MESSAGE);
  }

  public boolean isImportEnabled() {
    return myRbImport.isSelected() || myRbImportAuto.isSelected();
  }

  public File getSelectedFile() {
    return myRbImportAuto.isSelected() ? myGuessedOldConfig : new File(myPrevInstallation.getText());
  }
}