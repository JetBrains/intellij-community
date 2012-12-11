package org.jetbrains.android.newProject;

import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
* @author Eugene.Kudelevsky
*/
class AndroidLibraryModifiedSettingsStep extends AndroidModifiedSettingsStep {

  private final JTextField myPackageNameField;
  private boolean myPackageNameFieldChangedByUser;

  public AndroidLibraryModifiedSettingsStep(@NotNull AndroidModuleBuilder builder, @NotNull SettingsStep settingsStep) {
    super(builder, settingsStep);
    myPackageNameField = new JTextField();
    final SelectTemplateStep step = (SelectTemplateStep)settingsStep;
    updatePackageNameField(step);
    settingsStep.addSettingsField("Pa\u001Bckage name: ", myPackageNameField);

    myPackageNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myPackageNameFieldChangedByUser = true;
      }
    });

    step.getModuleNameField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (!myPackageNameFieldChangedByUser) {
          updatePackageNameField(step);
          myPackageNameFieldChangedByUser = false;
        }
      }
    });
  }

  private void updatePackageNameField(SelectTemplateStep settingsStep) {
    final String moduleName = settingsStep.getModuleNameField().getText().trim();

    if (moduleName.length() > 0) {
      myPackageNameField.setText(AndroidAppPropertiesEditor.getDefaultPackageNameByModuleName(moduleName));
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (!super.validate()) return false;
    final String message = AndroidAppPropertiesEditor.doValidatePackageName(true, getPackageName(), null);

    if (message.length() > 0) {
      throw new ConfigurationException(message);
    }
    return true;
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    myBuilder.setPackageName(getPackageName());
  }

  private String getPackageName() {
    return myPackageNameField.getText().trim();
  }
}
