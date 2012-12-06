/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;

public class EclipseProjectImportProvider extends ProjectImportProvider {

  private final EclipseProjectOpenProcessor myProcessor;

  public EclipseProjectImportProvider(final EclipseImportBuilder builder) {
    super(builder);
    myProcessor = new EclipseProjectOpenProcessor(builder);
  }

  public ModuleWizardStep[] createSteps(WizardContext context) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    return new ModuleWizardStep[]{new EclipseWorkspaceRootStep(context), new SelectEclipseImportedProjectsStep(context),
      stepFactory.createProjectJdkStep(context)/*, stepFactory.createNameAndLocationStep(context)*/};
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return myProcessor.canOpenProject(file);
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "<b>Eclipse</b> project (.project) or classpath (.classpath) file";
  }
}