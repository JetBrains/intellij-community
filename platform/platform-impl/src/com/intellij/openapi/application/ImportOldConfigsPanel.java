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
package com.intellij.openapi.application;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
  private JLabel myHomeLabel;
  private JRadioButton myRbImportAuto;
  private final File myGuessedOldConfig;

  public ImportOldConfigsPanel(final File guessedOldConfig, final Frame owner) {
    super(owner, true);
    myGuessedOldConfig = guessedOldConfig;

    init();
  }

  public ImportOldConfigsPanel(final File guessedOldConfig) {
    super((Dialog) null, true);
    myGuessedOldConfig = guessedOldConfig;
    init();
  }

  private void init() {
    new MnemonicHelper().register(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbDoNotImport);
    group.add(myRbImport);
    group.add(myRbImportAuto);
    myRbDoNotImport.setSelected(true);

    final String productName = getProductName(false);
    mySuggestLabel.setText(getTitleLabel(productName));
    myRbDoNotImport.setText(getDoNotImportLabel(productName));
    if(myGuessedOldConfig != null) {
      myRbImportAuto.setText(getAutoImportLabel(myGuessedOldConfig));
      myRbImportAuto.setSelected(true);
    } else {
      myRbImportAuto.setVisible(false);
    }
    myHomeLabel.setText(getHomeLabel(productName));

    myRbImport.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        update();
      }
    });

    if (myGuessedOldConfig != null) {
      myPrevInstallation.setText(myGuessedOldConfig.getParent());
    } if (SystemInfo.isMac) {
      myPrevInstallation.setText(findPreviousInstallationMac(productName));
    }
    else if (SystemInfo.isWindows) {
      String prevInstall = findPreviousInstallationWindows(productName);
      if (prevInstall != null) {
        myPrevInstallation.setText(prevInstall);
      }
    }

    myPrevInstallation.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        if (myLastSelection != null){
          fc = new JFileChooser(myLastSelection);
        }

        fc.setFileSelectionMode(SystemInfo.isMac ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);

        int returnVal = fc.showOpenDialog(ImportOldConfigsPanel.this);
        if (returnVal == JFileChooser.APPROVE_OPTION){
          File file = fc.getSelectedFile();
          if (file != null){
            myLastSelection = file;
            myPrevInstallation.setText(file.getAbsolutePath());
          }
        }
      }
    });

    myOkButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        close();
      }
    });

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myRootPanel);
    getRootPane().setDefaultButton(myOkButton);

    setTitle(ApplicationBundle.message("title.complete.installation"));

    update();
    pack();

    Dimension parentSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension ownSize = getPreferredSize();

    setLocation((parentSize.width - ownSize.width) / 2, (parentSize.height - ownSize.height) / 2);
  }

  protected String getProductName(boolean full) {
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    return full || namesInfo.getProductName().equals("IDEA") ? namesInfo.getFullProductName() : namesInfo.getProductName();
  }

  @Nullable
  private static String findPreviousInstallationWindows(String productName) {
      String programFiles = System.getenv("ProgramFiles");
    if (programFiles != null) {
      File jetbrainsHome = new File(programFiles, "JetBrains");
      if (jetbrainsHome.isDirectory()) {
        final File[] files = jetbrainsHome.listFiles();
        String latestVersion = null;
        File latestFile = null;
        for (File file : files) {
          if (file.isDirectory() && file.getName().startsWith(productName)) {
            String versionName = file.getName().substring(productName.length()).trim();
            // EAP builds don't have . in version number - ignore them
            if (versionName.indexOf('.') > 0) {
              if (latestVersion == null || StringUtil.compareVersionNumbers(latestVersion, versionName) > 0) {
                latestVersion = versionName;
                latestFile = file;
              }
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
    //noinspection HardCodedStringLiteral
    final String mostProbable = "/Applications/" + productName;
    if (new File(mostProbable).exists()) {
      return mostProbable;
    }
    return "/Applications";
  }

  private void close() {
    if (isImportEnabled()) {
      final String productWithVendor = getProductName(true);
      String instHome = myPrevInstallation.getText();
      if ("".equals(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      getEmptyHomeErrorText(productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (PathManager.getHomePath().equals(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      getCurrentHomeErrorText(productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (myRbImport.isSelected() && !ConfigImportHelper.isInstallationHomeOrConfig(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      getInvalidHomeErrorText(productWithVendor, instHome),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!new File(instHome).canRead()) {
        JOptionPane.showMessageDialog(this,
                                      getInaccessibleHomeErrorText(instHome),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    dispose();
  }

  public boolean isImportEnabled() {
    return myRbImport.isSelected() || myRbImportAuto.isSelected();
  }

  public File getSelectedFile() {
    return myRbImportAuto.isSelected() ? myGuessedOldConfig : new File(myPrevInstallation.getText());
  }

  private void update() {
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  public static void main(String[] args) {
    ImportOldConfigsPanel dlg = new ImportOldConfigsPanel(null);
    dlg.setVisible(true);
  }

  protected String getInaccessibleHomeErrorText(String instHome) {
    return ApplicationBundle.message("error.no.read.permissions", instHome);
  }

  protected String getInvalidHomeErrorText(String productWithVendor, String instHome) {
    return ApplicationBundle.message("error.does.not.appear.to.be.installation.home", instHome,
                                     productWithVendor);
  }

  protected String getCurrentHomeErrorText(String productWithVendor) {
    return ApplicationBundle.message("error.selected.current.installation.home",
                                     productWithVendor, productWithVendor);
  }

  protected String getEmptyHomeErrorText(String productWithVendor) {
    return ApplicationBundle.message("error.please.select.previous.installation.home", productWithVendor);
  }

  protected String getHomeLabel(String productName) {
    return ApplicationBundle.message("editbox.installation.home", productName);
  }

  protected String getAutoImportLabel(File guessedOldConfig) {
    return ApplicationBundle.message("radio.import.auto", guessedOldConfig.getAbsolutePath().replace(SystemProperties.getUserHome(), "~"));
  }

  protected String getDoNotImportLabel(String productName) {
    return ApplicationBundle.message("radio.do.not.import", productName);
  }

  protected String getTitleLabel(String productName) {
    return ApplicationBundle.message("label.you.can.import", productName);
  }

}
