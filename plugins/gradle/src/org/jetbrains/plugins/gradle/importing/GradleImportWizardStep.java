package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportWizardStep;

/**
 * Just a holder for the common useful functionality.
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 3:22 PM
 */
public abstract class GradleImportWizardStep extends ProjectImportWizardStep {

  protected GradleImportWizardStep(WizardContext context) {
    super(context);
  }

  @Override
  protected GradleProjectImportBuilder getBuilder() {
    return (GradleProjectImportBuilder)super.getBuilder();
  }
}
