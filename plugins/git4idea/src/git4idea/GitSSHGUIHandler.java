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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.PasswordPromptDialog;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

/**
 * Swing GUI handler for the SSH events
 */
public class GitSSHGUIHandler implements GitSSHService.Handler {
  /**
   * the project for the handler (used for popups)
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project a project to use
   */
  public GitSSHGUIHandler(Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public boolean verifyServerHostKey(final String hostname,
                                     final int port,
                                     final String serverHostKeyAlgorithm,
                                     final String fingerprint,
                                     final boolean isNew) {
    final String message;
    if (isNew) {
      message = GitBundle.message("ssh.new.host.key", hostname, port, fingerprint, serverHostKeyAlgorithm);
    }
    else {
      message = GitBundle.message("ssh.changed.host.key", hostname, port, fingerprint, serverHostKeyAlgorithm);
    }
    final int[] rc = new int[1];
    try {
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          rc[0] = Messages.showYesNoDialog(myProject, message, GitBundle.getString("ssh.confirm.key.titile"), null);
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException("dialog failed", e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("dialog failed", e);
    }
    return rc[0] == 0;
  }

  /**
   * {@inheritDoc}
   */
  public String askPassphrase(final String username, final String keyPath, final String lastError) {
    final String[] rc = new String[1];
    try {
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          showError(lastError);
          PasswordPromptDialog dialog = new PasswordPromptDialog(GitBundle.message("ssh.askPassphrase.message", keyPath, username),
                                                                 GitBundle.getString("ssh.ask.passphrase.title"), "");
          dialog.show();
          if (dialog.isOK()) {
            rc[0] = dialog.getPassword();
          }
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException("dialog failed", e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("dialog failed", e);
    }
    return rc[0];
  }

  /**
   * Show error if it is not empty
   *
   * @param lastError a error to show
   */
  private void showError(final String lastError) {
    if (lastError.length() != 0) {
      Messages.showErrorDialog(myProject, lastError, GitBundle.getString("ssh.error.title"));
    }
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  public Vector<String> replyToChallenge(final String username,
                                         final String name,
                                         final String instruction,
                                         final int numPrompts,
                                         final Vector<String> prompt,
                                         final Vector<Boolean> echo,
                                         final String lastError) {
    final Vector<String>[] rc = new Vector[1];
    try {
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          showError(lastError);
          GitSSHKeyboardInteractiveDialog dialog =
              new GitSSHKeyboardInteractiveDialog(name, numPrompts, instruction, prompt, echo, username);
          dialog.show();
          if (dialog.isOK()) {
            rc[0] = dialog.getResults();
          }
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException("dialog failed", e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("dialog failed", e);
    }
    return rc[0];
  }

  public String askPassword(final String username, final String lastError) {
    final String[] rc = new String[1];
    try {
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          showError(lastError);
          PasswordPromptDialog dialog =
              new PasswordPromptDialog(GitBundle.message("ssh.password.message", username), GitBundle.getString("ssh.password.title"), "");
          dialog.show();
          if (dialog.isOK()) {
            rc[0] = dialog.getPassword();
          }
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException("dialog failed", e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException("dialog failed", e);
    }
    return rc[0];
  }

  /**
   * Keyboard interactive input dialog
   */
  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private class GitSSHKeyboardInteractiveDialog extends DialogWrapper {
    /**
     * input fields
     */
    JTextComponent[] inputs;
    /**
     * root panel
     */
    JPanel contents;
    /**
     * number of prompts
     */
    private final int myNumPrompts;
    /**
     * Instructions
     */
    private final String myInstruction;
    /**
     * Prompts
     */
    private final Vector<String> myPrompt;
    /**
     * Array of echo values
     */
    private final Vector<Boolean> myEcho;
    /**
     * A name of user
     */
    private final String myUsername;

    public GitSSHKeyboardInteractiveDialog(String name,
                                           final int numPrompts,
                                           final String instruction,
                                           final Vector<String> prompt,
                                           final Vector<Boolean> echo,
                                           final String username) {
      super(myProject, true);
      myNumPrompts = numPrompts;
      myInstruction = instruction;
      myPrompt = prompt;
      myEcho = echo;
      myUsername = username;
      setTitle(GitBundle.message("ssh.keyboard.interactive.title", name));
      init();
      setResizable(true);
      setModal(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JComponent createCenterPanel() {
      if (contents == null) {
        int line = 0;
        contents = new JPanel(new GridBagLayout());
        inputs = new JTextComponent[myNumPrompts];
        GridBagConstraints c;
        Insets insets = new Insets(1, 1, 1, 1);
        if (myInstruction.length() != 0) {
          JLabel instructionLabel = new JLabel(myInstruction);
          c = new GridBagConstraints();
          c.insets = insets;
          c.gridx = 0;
          c.gridy = line;
          c.gridwidth = 2;
          c.weightx = 1;
          c.fill = GridBagConstraints.HORIZONTAL;
          c.anchor = GridBagConstraints.WEST;
          line++;
          contents.add(instructionLabel, c);
        }
        c = new GridBagConstraints();
        c.insets = insets;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = line;
        contents.add(new JLabel(GitBundle.getString("ssh.keyboard.interactive.username")), c);
        c = new GridBagConstraints();
        c.insets = insets;
        c.gridx = 1;
        c.gridy = line;
        c.gridwidth = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        contents.add(new JLabel(myUsername), c);
        line++;
        for (int i = 0; i < myNumPrompts; i++) {
          c = new GridBagConstraints();
          c.insets = insets;
          c.anchor = GridBagConstraints.WEST;
          c.gridx = 0;
          c.gridy = line;
          JLabel promptLabel = new JLabel(myPrompt.get(i));
          contents.add(promptLabel, c);
          c = new GridBagConstraints();
          c.insets = insets;
          c.gridx = 1;
          c.gridy = line;
          c.gridwidth = 1;
          c.weightx = 1;
          c.fill = GridBagConstraints.HORIZONTAL;
          c.anchor = GridBagConstraints.WEST;
          if (myEcho.get(i).booleanValue()) {
            inputs[i] = new JTextField(32);
          }
          else {
            inputs[i] = new JPasswordField(32);
          }
          contents.add(inputs[i], c);
          line++;
        }
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = line;
        c.gridwidth = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.VERTICAL;
        c.anchor = GridBagConstraints.CENTER;
        contents.add(new JPanel(), c);
      }
      return contents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction(), getCancelAction()};
    }

    /**
     * @return text entered at prompt
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> getResults() {
      Vector<String> rc = new Vector<String>(myNumPrompts);
      for (int i = 0; i < myNumPrompts; i++) {
        rc.add(inputs[i].getText());
      }
      return rc;
    }
  }
}
