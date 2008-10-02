/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import git4idea.commands.GitCommand;
import git4idea.validators.GitBranchNameValidator;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * A dialog for the git clone options
 *
 * @author Constantine.Plotnikov
 */
public class GitCheckoutDialog extends DialogWrapper {
  /**
   * The parttern for SSH urls in form [user@]host:path
   */
  private static final Pattern SSH_URL_PATTERN;
  static {
    // TODO make real URL pattern
    @NonNls final String ch = "[\\p{ASCII}&&[\\p{Graph}]&&[^@:/]]";
    @NonNls final String host = ch+"+(?:\\."+ch+"+)*";
    @NonNls final String path = "/?"+ch+"+(?:/"+ch+"+)*/?";
    @NonNls final String all = "(?:"+ch+"+@)?"+host+":"+path;
    SSH_URL_PATTERN = Pattern.compile(all);
  }
  /**
   * repository URL
   */
  private JTextField myRepositoryURL;
  /**
   * parent directory
   */
  private TextFieldWithBrowseButton myParentDirectory;
  /**
   * test repository URL button
   */
  private JButton myTestButton;
  /**
   * the repository URL at the time of the last test
   */
  private String myTestURL;
  /**
   * the test result of the last test or null if not tested
   */
  private Boolean myTestResult;
  /**
   * directory name button
   */
  private JTextField myDirectoryName;
  /**
   * current default directory name
   */
  private String myDefaultDirectoryName = "";
  /**
   * origin name for cloned repository (-o option)
   */
  private JTextField myOriginName;
  /**
   * panel that wraps it all
   */
  private JPanel myPanel;
  /**
   * the project for checkout
   */
  private Project myProject;
  /**
   * the settings for the git
   */
  private GitVcsSettings mySettings;

  /**
   * A constructor
   *
   * @param project  a project for checkout action
   * @param settings a Git settings
   */
  public GitCheckoutDialog(Project project, GitVcsSettings settings) {
    super(project, true);
    myProject = project;
    mySettings = settings;
    init();
    initListeners();
    setTitle(GitBundle.getString("clone.title"));
  }

  /**
   * @return the URL of the source repository
   */
  public String getSourceRepositoryURL() {
    return myRepositoryURL.getText();
  }

  /**
   * @return the parent directory for checkout
   */
  public String getParentDirectory() {
    return myParentDirectory.getText();
  }

  /**
   * @return the directory name to checkout to
   */
  public String getDirectoryName() {
    return myDirectoryName.getText();
  }

  /**
   * @return the origin name to use
   */
  public String getOriginName() {
    return myOriginName.getText();
  }


  /**
   * Custom component creation code
   */
  private void createUIComponents() {

  }

  /**
   * Init components
   */
  private void initListeners() {
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(GitBundle.getString("clone.destination.directory.title"));
    fcd.setDescription(GitBundle.getString("clone.destination.directory.description"));
    fcd.setHideIgnored(false);
    myParentDirectory.addBrowseFolderListener(fcd.getTitle(), fcd.getDescription(), myProject, fcd);
    final DocumentListener updateOkButtonListener = new DocumentListener() {
      // update Ok button state depending on the current state of the fields
      public void insertUpdate(final DocumentEvent e) {
        updateOkButton();
      }

      public void removeUpdate(final DocumentEvent e) {
        updateOkButton();
      }

      public void changedUpdate(final DocumentEvent e) {
        updateOkButton();
      }
    };
    myParentDirectory.getChildComponent().getDocument().addDocumentListener(updateOkButtonListener);
    myDirectoryName.getDocument().addDocumentListener(updateOkButtonListener);
    myOriginName.getDocument().addDocumentListener(updateOkButtonListener);
    myRepositoryURL.getDocument().addDocumentListener(new DocumentListener() {
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
        final String url = myRepositoryURL.getText();
        myTestButton.setEnabled(url.length() != 0);
        if (myDefaultDirectoryName.equals(myDirectoryName.getText()) || myDirectoryName.getText().length() == 0) {
          // modify field if it was unmodified or blank
          myDefaultDirectoryName = defaultDirectoryName(url);
          myDirectoryName.setText(myDefaultDirectoryName);
        }
        updateOkButton();
      }
    });
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        try {
          myTestURL = myRepositoryURL.getText();
          try {
            GitCommand command = new GitCommand(myProject, mySettings, GitUtil.getTempDir());
            command.checkRepository(myTestURL);
            Messages.showInfoMessage(myTestButton, GitBundle.message("clone.test.success.message", myTestURL),
                                     GitBundle.getString("clone.test.success"));
            myTestResult = Boolean.TRUE;
          }
          catch (VcsException ex) {
            myTestResult = Boolean.FALSE;
            GitUtil.showOperationError(myProject, ex, "connection test");
          }
        }
        finally {
          updateOkButton();
        }
      }
    });
    setOKActionEnabled(false);
  }

  /**
   * Check fields and display error in the wrapper if there is a problem
   */
  private void updateOkButton() {
    if (!checkRepositoryURL()) {
      return;
    }
    if (!checkDestination()) {
      return;
    }
    if (!checkOrigin()) {
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Check origin and set appropriate error text if there are problems
   *
   * @return true if origin name is OK.
   */
  private boolean checkOrigin() {
    String origin = myOriginName.getText();
    if (origin.length() != 0 && !GitBranchNameValidator.INSTANCE.checkInput(origin)) {
      setErrorText(GitBundle.getString("clone.invalid.origin"));
      setOKActionEnabled(false);
      return false;
    }
    return true;
  }


  /**
   * Check destination directory and set appropriate error text if there are problems
   *
   * @return true if destination components are OK.
   */
  private boolean checkDestination() {
    if (myParentDirectory.getText().length() == 0 || myDirectoryName.getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    File file = new File(myParentDirectory.getText(), myDirectoryName.getText());
    if (file.exists()) {
      setErrorText(GitBundle.message("clone.destination.exists.error", file));
      setOKActionEnabled(false);
      return false;
    }
    else if (!file.getParentFile().exists()) {
      setErrorText(GitBundle.message("clone.parent.missing.error", file.getParent()));
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
    String repository = myRepositoryURL.getText();
    if (repository.length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    if (myTestResult != null && repository.equals(myTestURL)) {
      if( !myTestResult.booleanValue()) {
        setErrorText(GitBundle.getString("clone.test.failed.error"));
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
    // check if ssh url pattern
    if(SSH_URL_PATTERN.matcher(repository).matches()) {
      return true;
    }
    try {
      File file = new File(repository);
      if (file.exists()) {
        if (!file.isDirectory()) {
          setErrorText(GitBundle.getString("clone.url.is.not.directory.error"));
          setOKActionEnabled(false);
        }
        return true;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }
    setErrorText(GitBundle.getString("clone.invalid.url"));
    setOKActionEnabled(false);
    return false;
  }

  /**
   * Get default name for checkouted directory
   *
   * @param url an URL to checkout
   * @return a default repository name
   */
  private static String defaultDirectoryName(final String url) {
    String nonSystemName;
    //noinspection HardCodedStringLiteral
    if (url.endsWith("/.git") || url.endsWith(File.separator + ".git")) {
      nonSystemName = url.substring(0, url.length() - 5);
    }
    else {
      //noinspection HardCodedStringLiteral
      if (url.endsWith(".git")) {
        nonSystemName = url.substring(0, url.length() - 4);
      }
      else {
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
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return "GitCheckoutDialog";
  }
}
