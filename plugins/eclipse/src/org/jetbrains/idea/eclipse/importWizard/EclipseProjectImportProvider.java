// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseBundle;

public final class EclipseProjectImportProvider extends ProjectImportProvider {
  private final EclipseProjectOpenProcessor myProcessor = new EclipseProjectOpenProcessor();

  @Override
  protected ProjectImportBuilder doGetBuilder() {
    return ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(EclipseImportBuilder.class);
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    return new ModuleWizardStep[]{new EclipseWorkspaceRootStep(context), new SelectEclipseImportedProjectsStep(context),
      new EclipseCodeStyleImportStep(context),
      stepFactory.createProjectJdkStep(context)/*, stepFactory.createNameAndLocationStep(context)*/};
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return myProcessor.canOpenProject(file);
  }

  @NotNull
  @Override
  public String getFileSample() {
    return EclipseBundle.message("eclipse.file.to.import.description");
  }
}