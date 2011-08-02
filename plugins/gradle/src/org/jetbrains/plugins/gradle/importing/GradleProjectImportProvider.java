package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;

/**
 * @author Denis Zhdanov
 * @since 7/29/11 3:45 PM
 */
public class GradleProjectImportProvider extends ProjectImportProvider {

  public GradleProjectImportProvider(ProjectImportBuilder builder) {
    // TODO den implement
    super(builder);
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    // TODO den implement
    return new ModuleWizardStep[0];
  }
}
