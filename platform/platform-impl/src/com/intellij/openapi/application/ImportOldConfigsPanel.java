/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

  public ImportOldConfigsPanel(final Frame owner) {
    super(owner, true);

    init();
  }

  public ImportOldConfigsPanel() {
    super((Dialog) null, true);

    init();
  }

  private void init() {
    new MnemonicHelper().register(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbDoNotImport);
    group.add(myRbImport);
    myRbDoNotImport.setSelected(true);

    final ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    final String productName = namesInfo.getProductName().equals("IDEA") ? namesInfo.getFullProductName() : namesInfo.getProductName();
    mySuggestLabel.setText(ApplicationBundle.message("label.you.can.import", productName));
    myRbDoNotImport.setText(ApplicationBundle.message("radio.do.not.import", productName));
    myHomeLabel.setText(ApplicationBundle.message("editbox.installation.home", productName));

    myRbImport.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        update();
      }
    });

    if (SystemInfo.isMac) {
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

        if (!SystemInfo.isMac) {
          fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }

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
      final String productWithVendor = ApplicationNamesInfo.getInstance().getFullProductName();
      String instHome = myPrevInstallation.getText();
      if ("".equals(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      ApplicationBundle.message("error.please.select.previous.installation.home", productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (PathManager.getHomePath().equals(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      ApplicationBundle.message("error.selected.current.installation.home",
                                                                productWithVendor, productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!ConfigImportHelper.isInstallationHome(instHome)) {
        JOptionPane.showMessageDialog(this,
                                      ApplicationBundle.message("error.does.not.appear.to.be.installation.home", instHome,
                                                                productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!new File(instHome).canRead()) {
        JOptionPane.showMessageDialog(this,
                                      ApplicationBundle.message("error.no.read.permissions", instHome),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    dispose();
  }

  public boolean isImportEnabled() {
    return myRbImport.isSelected();
  }

  public File getSelectedFile() {
    return new File(myPrevInstallation.getText());
  }

  private void update() {
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  public static void main(String[] args) {
    ImportOldConfigsPanel dlg = new ImportOldConfigsPanel();
    dlg.setVisible(true);
  }
}
