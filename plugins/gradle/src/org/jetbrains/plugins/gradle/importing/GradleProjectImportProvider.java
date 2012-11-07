package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.wizard.adjust.GradleAdjustImportSettingsStep;
import org.jetbrains.plugins.gradle.importing.wizard.select.GradleSelectProjectStep;

/**
 * @author Denis Zhdanov
 * @since 7/29/11 3:45 PM
 */
public class GradleProjectImportProvider extends ProjectImportProvider {

  public GradleProjectImportProvider(GradleProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return new ModuleWizardStep[] { new GradleSelectProjectStep(context), new GradleAdjustImportSettingsStep(context) };
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "*.gradle";
  }
}
