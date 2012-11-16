package org.jetbrains.android.newProject;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.run.AndroidRunConfiguration;
import org.jetbrains.android.run.AndroidRunConfigurationType;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
class AndroidTestModifiedSettingsStep extends AndroidModifiedSettingsStep {
  private final AndroidModulesComboBox myModulesCombo;
  private final Project myProject;

  public AndroidTestModifiedSettingsStep(@NotNull AndroidModuleBuilder builder, @NotNull SettingsStep settingsStep) {
    super(builder, settingsStep);
    myModulesCombo = new AndroidModulesComboBox();
    myProject = settingsStep.getContext().getProject();
    assert myProject != null : "test module can't be created as first module";
    myModulesCombo.init(myProject);
    settingsStep.addSettingsField("\u001BTested module: ", myModulesCombo);
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    final Module testedModule = myModulesCombo.getModule();
    myBuilder.setTestedModule(testedModule);
    myBuilder.setTargetSelectionMode(chooseTargetSelectionMode(testedModule));
  }

  @NotNull
  private TargetSelectionMode chooseTargetSelectionMode(@NotNull Module testedModule) {
    final RunConfiguration[] androidConfigurations =
      RunManager.getInstance(myProject).getConfigurations(AndroidRunConfigurationType.getInstance());

    for (RunConfiguration configuration : androidConfigurations) {
      final AndroidRunConfiguration cfg = (AndroidRunConfiguration)configuration;
      final Module module = cfg.getConfigurationModule().getModule();

      if (testedModule.equals(module)) {
        return cfg.getTargetSelectionMode();
      }
    }
    return TargetSelectionMode.EMULATOR;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    AndroidTestPropertiesEditor.doValidate(myModulesCombo.getModule());
    return true;
  }
}
