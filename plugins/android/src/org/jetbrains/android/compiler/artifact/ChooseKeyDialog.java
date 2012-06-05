package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.exportSignedPackage.NewKeyForm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ChooseKeyDialog extends DialogWrapper {
  private JPanel myNewKeyPanel;
  private JBRadioButton myCreateNewKeyRadioButton;
  private JBRadioButton myUseExistingKeyRadioButton;
  private JComboBox myKeyCombo;
  private JPanel myPanel;

  private final NewKeyForm myNewKeyForm = new MyNewKeyForm();
  private final Project myProject;
  private final String myKeyStorePath;
  private final char[] myKeyStorePassword;
  private final List<String> myExistingKeys;

  public ChooseKeyDialog(@NotNull Project project,
                         @NotNull String keyStorePath,
                         @NotNull char[] password,
                         @NotNull List<String> existingKeys,
                         @Nullable String keyToSelect) {
    super(project);
    myProject = project;
    myKeyStorePath = keyStorePath;
    myKeyStorePassword = password;
    myExistingKeys = existingKeys;
    myKeyCombo.setModel(new CollectionComboBoxModel(existingKeys, existingKeys.get(0)));

    if (keyToSelect != null && existingKeys.contains(keyToSelect)) {
      myKeyCombo.setSelectedItem(keyToSelect);
    }
    myNewKeyPanel.add(myNewKeyForm.getContentPanel(), BorderLayout.CENTER);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myNewKeyPanel, myCreateNewKeyRadioButton.isSelected(), true);
      }
    };
    myCreateNewKeyRadioButton.addActionListener(listener);
    myUseExistingKeyRadioButton.addActionListener(listener);

    final boolean useExisting = existingKeys.size() > 0;
    myUseExistingKeyRadioButton.setSelected(useExisting);
    myCreateNewKeyRadioButton.setSelected(!useExisting);
    UIUtil.setEnabled(myNewKeyPanel, !useExisting, true);

    setTitle("Choose Key");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreateNewKeyRadioButton.isSelected()) {
      try {
        myNewKeyForm.createKey();
      }
      catch (CommitStepException e) {
        Messages.showErrorDialog(myPanel, e.getMessage(), CommonBundle.getErrorTitle());
        return;
      }
    }
    super.doOKAction();
  }

  @Nullable
  public String getChosenKey() {
    return myUseExistingKeyRadioButton.isSelected()
           ? (String)myKeyCombo.getSelectedItem()
           : myNewKeyForm.getKeyAlias();
  }

  @Nullable
  public char[] getChosenKeyPassword() {
    return myCreateNewKeyRadioButton.isSelected()
           ? myNewKeyForm.getKeyPassword()
           : null;
  }

  private class MyNewKeyForm extends NewKeyForm {
    @Override
    protected List<String> getExistingKeyAliasList() {
      return myExistingKeys;
    }

    @NotNull
    @Override
    protected Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    protected char[] getKeyStorePassword() {
      return myKeyStorePassword;
    }

    @NotNull
    @Override
    protected String getKeyStoreLocation() {
      return myKeyStorePath;
    }
  }
}
