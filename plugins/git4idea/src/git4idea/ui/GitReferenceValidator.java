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
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.util.GitUIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The class setups validation for references in the text fields.
 */
public class GitReferenceValidator {
  /**
   * The result of last validation
   */
  private boolean myLastResult;
  /**
   * The text that was used for last validation
   */
  private String myLastResultText = null;
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The git root combobox
   */
  private final JComboBox myGitRoot;
  /**
   * The text field that contains object reference
   */
  private final JTextField myTextField;
  /**
   * The button that initiates validation action
   */
  private final JButton myButton;

  /**
   * A constructor from fields
   *
   * @param project       the project to use
   * @param gitRoot       the git root directory
   * @param textField     the text field that contains object reference
   * @param button        the button that initiates validation action
   * @param statusChanged the action that is invoked when validation status changed
   */
  public GitReferenceValidator(final Project project,
                               final JComboBox gitRoot,
                               final JTextField textField,
                               final JButton button,
                               final Runnable statusChanged) {
    myProject = project;
    myGitRoot = gitRoot;
    myTextField = textField;
    myButton = button;
    myGitRoot.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myLastResult = false;
        myLastResultText = null;
      }
    });
    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        // note that checkOkButton is called in other listener
        myButton.setEnabled(myTextField.getText().trim().length() != 0);
      }
    });
    myButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final String revisionExpression = myTextField.getText();
        myLastResultText = revisionExpression;
        myLastResult = false;
        try {
          GitRevisionNumber revision = GitRevisionNumber.resolve(myProject, gitRoot(), revisionExpression);
          GitUtil.showSubmittedFiles(myProject, revision.asString(), gitRoot(), false, false);
          myLastResult = true;
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "Validating revision: " + revisionExpression);
        }
        if (statusChanged != null) {
          statusChanged.run();
        }
      }
    });
    myButton.setEnabled(myTextField.getText().length() != 0);
  }

  /**
   * @return true if the reference is known to be invalid
   */
  public boolean isInvalid() {
    final String revisionExpression = myTextField.getText();
    return revisionExpression.equals(myLastResultText) && !myLastResult;
  }

  /**
   * @return currently selected git root
   */
  private VirtualFile gitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }
}
