package org.jetbrains.plugins.gradle.manage.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.manage.GradleProjectImportBuilder;

/**
 * Just a holder for the common useful functionality.
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 3:22 PM
 */
public abstract class AbstractImportFromGradleWizardStep extends ProjectImportWizardStep {

  protected AbstractImportFromGradleWizardStep(@NotNull WizardContext context) {
    super(context);
  }

  @NotNull
  @Override
  public WizardContext getWizardContext() {
    return super.getWizardContext();
  }

  @Override
  @Nullable
  protected GradleProjectImportBuilder getBuilder() {
    return (GradleProjectImportBuilder)getWizardContext().getProjectBuilder();
  }
}
