/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.newProjectWizard.projectImport.ProjectImportProvider;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;

public class EclipseProjectImportProvider extends ProjectImportProvider {
  public EclipseProjectImportProvider(final EclipseImportBuilder builder) {
    super(builder);
  }

  public ModuleWizardStep[] createSteps(WizardContext context) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    return new ModuleWizardStep[]{new EclipseWorkspaceRootStep(context), new SelectEclipseImportedProjectsStep(context),
      stepFactory.createProjectJdkStep(context), stepFactory.createNameAndLocationStep(context)};
  }
}