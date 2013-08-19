package org.jetbrains.plugins.github.ui;

import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * @author oleg
 * @date 10/22/10
 */
public class GithubSharePanel {
  private JPanel myPanel;
  private JTextField myRepositoryTextField;
  private JCheckBox myPrivateCheckBox;
  private JTextArea myDescriptionTextArea;
  private final GithubShareDialog myGithubShareDialog;

  public GithubSharePanel(final GithubShareDialog githubShareDialog) {
    myGithubShareDialog = githubShareDialog;
    myRepositoryTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myGithubShareDialog.updateOkButton();
      }
    });
    myPrivateCheckBox.setSelected(false);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusComponent() {
    return myRepositoryTextField;
  }

  public String getRepositoryName() {
    return myRepositoryTextField.getText().trim();
  }

  public void setRepositoryName(final String name) {
    myRepositoryTextField.setText(name);
  }

  public boolean isPrivate() {
    return myPrivateCheckBox.isSelected();
  }

  public String getDescription() {
    return myDescriptionTextArea.getText().trim();
  }

  public void setPrivateRepoAvailable(final boolean privateRepoAllowed) {
    if (!privateRepoAllowed) {
      myPrivateCheckBox.setEnabled(false);
      myPrivateCheckBox.setToolTipText("Your account doesn't support private repositories");
    }
  }
}
