/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.openapi.module.StdModuleTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class GroovySdkForProjectFromSourcesStep extends GroovySdkWizardStepBase {
  private final MvcProjectStructureDetector myDetector;
  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;

  public GroovySdkForProjectFromSourcesStep(MvcProjectStructureDetector detector, ProjectFromSourcesBuilder builder,
                                            ProjectDescriptor projectDescriptor,
                                            MvcFramework framework) {
    super(framework, builder.getContext(), builder.getBaseProjectPath());
    myDetector = detector;
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    List<ModuleDescriptor> modules = new ArrayList<>();
    for (DetectedProjectRoot root : myBuilder.getProjectRoots(myDetector)) {
      final ModuleDescriptor descriptor = new ModuleDescriptor(root.getDirectory(), StdModuleTypes.JAVA, Collections.<DetectedSourceRoot>emptyList());
      descriptor.addConfigurationUpdater(createModuleConfigurationUpdater());
      modules.add(descriptor);
    }
    myProjectDescriptor.setModules(modules);
  }
}
