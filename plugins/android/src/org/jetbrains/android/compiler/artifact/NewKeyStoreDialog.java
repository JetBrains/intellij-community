package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.android.exportSignedPackage.NewKeyForm;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class NewKeyStoreDialog extends DialogWrapper {
  private JPanel myNewKeyPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myKeyStorePathField;
  private JPasswordField myPasswordField;
  private JPasswordField myConfirmedPassword;

  private final NewKeyForm myNewKeyForm;
  private final Project myProject;

  public NewKeyStoreDialog(@NotNull Project project, @NotNull String defaultKeyStorePath) {
    super(project);
    myProject = project;
    myKeyStorePathField.setText(defaultKeyStorePath);
    setTitle("Create New Key Store");
    myNewKeyForm = new MyNewKeyForm();
    myNewKeyPanel.add(myNewKeyForm.getContentPanel(), BorderLayout.CENTER);

    myKeyStorePathField.addActionListener(new SaveFileListener(myPanel, myKeyStorePathField, AndroidBundle.message(
      "android.extract.package.choose.keystore.title")) {
      @Override
      protected String getDefaultLocation() {
        return getKeyStorePath();
      }
    });
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myKeyStorePathField;
  }

  @Override
  protected void doOKAction() {
    if (getKeyStorePath().length() == 0) {
      Messages.showErrorDialog(myPanel, "Specify key store path", CommonBundle.getErrorTitle());
      return;
    }

    try {
      AndroidUtils.checkNewPassword(myPasswordField, myConfirmedPassword);
      myNewKeyForm.createKey();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(myPanel, e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }
    super.doOKAction();
  }

  @NotNull
  public String getKeyStorePath() {
    return myKeyStorePathField.getText().trim();
  }

  @NotNull
  public char[] getKeyStorePassword() {
    return myPasswordField.getPassword();
  }

  @NotNull
  public String getKeyAlias() {
    return myNewKeyForm.getKeyAlias();
  }

  @NotNull
  public char[] getKeyPassword() {
    return myNewKeyForm.getKeyPassword();
  }

  private class MyNewKeyForm extends NewKeyForm {

    @Override
    protected List<String> getExistingKeyAliasList() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    protected Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    protected char[] getKeyStorePassword() {
      return NewKeyStoreDialog.this.getKeyStorePassword();
    }

    @NotNull
    @Override
    protected String getKeyStoreLocation() {
      return getKeyStorePath();
    }
  }
}
