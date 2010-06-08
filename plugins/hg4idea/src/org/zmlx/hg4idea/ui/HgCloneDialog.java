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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgCommandResult;
import org.zmlx.hg4idea.command.HgIdentifyCommand;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ResourceBundle;

/**
 * A dialog for the mercurial clone options
 */
public class HgCloneDialog extends DialogWrapper {

  /**
   * repository URL
   */
  private JTextField repositoryURL;
  /**
   * parent directory
   */
  private TextFieldWithBrowseButton parentDirectory;
  /**
   * test repository URL button
   */
  private JButton testButton;
  /**
   * the repository URL at the time of the last test
   */
  private String testURL;
  /**
   * the test result of the last test or null if not tested
   */
  private Boolean testResult;
  /**
   * directory name button
   */
  private JTextField directoryName;
  /**
   * current default directory name
   */
  private String defaultDirectoryName = "";
  /**
   * panel that wraps it all
   */
  private JPanel clonePanel;
  /**
   * the project for checkout
   */
  private final Project project;

  /**
   * A constructor
   *
   * @param project a project for checkout action
   */
  public HgCloneDialog(Project project) {
    super(project, true);
    this.project = project;
    init();
    initListeners();
    setTitle(HgVcsMessages.message("hg4idea.clone.title"));
    setOKButtonText(HgVcsMessages.message("hg4idea.clone.button.clone"));
  }

  /**
   * @return the URL of the source repository
   */
  public String getSourceRepositoryURL() {
    return repositoryURL.getText();
  }

  /**
   * @return the parent directory for checkout
   */
  public String getParentDirectory() {
    return parentDirectory.getText();
  }

  /**
   * @return the directory name to checkout to
   */
  public String getDirectoryName() {
    return directoryName.getText();
  }

  /**
   * Init components
   */
  private void initListeners() {
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(HgVcsMessages.message("hg4idea.clone.parent.directory.title"));
    fcd.setDescription(HgVcsMessages.message("hg4idea.clone.parent.directory.description"));
    fcd.setHideIgnored(false);
    parentDirectory.addActionListener(
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(fcd.getTitle(), fcd.getDescription(), parentDirectory,
        project, fcd, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
        @Override
        protected VirtualFile getInitialFile() {
          // suggest project base directory only if nothing is typed in the component.
          String text = getComponentText();
          if (text.length() == 0) {
            VirtualFile file = project.getBaseDir();
            if (file != null) {
              return file;
            }
          }
          return super.getInitialFile();
        }
      });
    final DocumentListener updateOkButtonListener = new DocumentListener() {
      // update Ok button state depending on the current state of the fields
      public void insertUpdate(final DocumentEvent e) {
        updateCloneButton();
      }

      public void removeUpdate(final DocumentEvent e) {
        updateCloneButton();
      }

      public void changedUpdate(final DocumentEvent e) {
        updateCloneButton();
      }
    };
    parentDirectory.getChildComponent().getDocument().addDocumentListener(updateOkButtonListener);
    directoryName.getDocument().addDocumentListener(updateOkButtonListener);
    repositoryURL.getDocument().addDocumentListener(new DocumentListener() {
      // enable test button only if something is entered in repository URL
      public void insertUpdate(final DocumentEvent e) {
        changed();
      }

      public void removeUpdate(final DocumentEvent e) {
        changed();
      }

      public void changedUpdate(final DocumentEvent e) {
        changed();
      }

      private void changed() {
        final String url = repositoryURL.getText();
        testButton.setEnabled(url.length() != 0);
        if (defaultDirectoryName.equals(directoryName.getText()) || directoryName.getText().length() == 0) {
          // modify field if it was unmodified or blank
          defaultDirectoryName = defaultDirectoryName(url);
          directoryName.setText(defaultDirectoryName);
        }
        updateCloneButton();
      }
    });
    testButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        testURL = repositoryURL.getText();
        boolean succeeded = testRepository(project, testURL);
        if (succeeded) {
          Messages.showInfoMessage(testButton, HgVcsMessages.message("hg4idea.clone.test.success.message", testURL),
            HgVcsMessages.message("hg4idea.clone.test.success"));
          testResult = Boolean.TRUE;
        } else {
          testResult = Boolean.FALSE;
        }
        updateCloneButton();
      }
    });
    setOKActionEnabled(false);
  }

  /**
   * Check fields and display error in the wrapper if there is a problem
   */
  private void updateCloneButton() {
    if (!checkRepositoryURL()) {
      return;
    }
    if (!checkDestination()) {
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Check destination directory and set appropriate error text if there are problems
   *
   * @return true if destination components are OK.
   */
  private boolean checkDestination() {
    if (parentDirectory.getText().length() == 0 || directoryName.getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    File file = new File(parentDirectory.getText(), directoryName.getText());
    if (file.exists()) {
      setErrorText(HgVcsMessages.message("hg4idea.clone.error.destination.directory.exists", file));
      setOKActionEnabled(false);
      return false;
    } else if (!file.getParentFile().exists()) {
      setErrorText(HgVcsMessages.message("hg4idea.clone.error.parent.directory.missing", file.getParent()));
      setOKActionEnabled(false);
      return false;
    }
    return true;
  }

  /**
   * Check repository URL and set appropriate error text if there are problems
   *
   * @return true if repository URL is OK.
   */
  private boolean checkRepositoryURL() {
    String repository = repositoryURL.getText();
    if (repository.length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    if (testResult != null && repository.equals(testURL)) {
      if (!testResult) {
        setErrorText(HgVcsMessages.message("hg4idea.clone.error.test.failed"));
        setOKActionEnabled(false);
        return false;
      } else {
        return true;
      }
    }
    try {
      if (new URI(repository).isAbsolute()) {
        return true;
      }
    }
    catch (URISyntaxException urlExp) {
      // do nothing
    }
    try {
      File file = new File(repository);
      if (file.exists()) {
        if (!file.isDirectory()) {
          setErrorText(HgVcsMessages.message("hg4idea.clone.error.repository.url.is.not.directory"));
          setOKActionEnabled(false);
        }
        return true;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }
    setErrorText(HgVcsMessages.message("hg4idea.clone.error.repository.url"));
    setOKActionEnabled(false);
    return false;
  }

  /**
   * Get default name for checked out directory
   *
   * @param url an URL to checkout
   * @return a default repository name
   */
  private static String defaultDirectoryName(final String url) {
    String nonSystemName;
    //noinspection HardCodedStringLiteral
    if (url.endsWith("/.hg") || url.endsWith(File.separator + ".hg")) {
      nonSystemName = url.substring(0, url.length() - 5);
    } else {
      //noinspection HardCodedStringLiteral
      if (url.endsWith(".hg")) {
        nonSystemName = url.substring(0, url.length() - 4);
      } else {
        nonSystemName = url;
      }
    }
    int i = nonSystemName.lastIndexOf('/');
    if (i == -1 && File.separatorChar != '/') {
      i = nonSystemName.lastIndexOf(File.separatorChar);
    }
    return i >= 0 ? nonSystemName.substring(i + 1) : "";
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return clonePanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return "HgCloneDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return repositoryURL;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Hg.CloneRepository";
  }

  private boolean testRepository(Project project, final String repositoryUrl) {
    HgIdentifyCommand identifyCommand = new HgIdentifyCommand(project);
    identifyCommand.setSource(repositoryUrl);
    HgCommandResult result = identifyCommand.execute();

    return result.getExitValue() == 0;
  }

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    clonePanel = new JPanel();
    clonePanel.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("org/zmlx/hg4idea/HgVcsMessages").getString("hg4idea.clone.repository.url"));
    clonePanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    clonePanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    clonePanel.add(spacer2, new GridConstraints(3, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    repositoryURL = new JTextField();
    clonePanel.add(repositoryURL, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JLabel label2 = new JLabel();
    this.$$$loadLabelText$$$(label2, ResourceBundle.getBundle("org/zmlx/hg4idea/HgVcsMessages").getString("hg4idea.clone.parent.directory"));
    clonePanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    parentDirectory = new TextFieldWithBrowseButton();
    clonePanel.add(parentDirectory, new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label3 = new JLabel();
    this.$$$loadLabelText$$$(label3, ResourceBundle.getBundle("org/zmlx/hg4idea/HgVcsMessages").getString("hg4idea.clone.directory.name"));
    clonePanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    testButton = new JButton();
    this.$$$loadButtonText$$$(testButton, ResourceBundle.getBundle("org/zmlx/hg4idea/HgVcsMessages").getString("hg4idea.clone.button.test"));
    clonePanel.add(testButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    directoryName = new JTextField();
    clonePanel.add(directoryName, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final Spacer spacer3 = new Spacer();
    clonePanel.add(spacer3, new GridConstraints(2, 2, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    label1.setLabelFor(repositoryURL);
    label3.setLabelFor(directoryName);
  }

  /**
   * @noinspection ALL
   */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /**
   * @noinspection ALL
   */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return clonePanel;
  }
}
