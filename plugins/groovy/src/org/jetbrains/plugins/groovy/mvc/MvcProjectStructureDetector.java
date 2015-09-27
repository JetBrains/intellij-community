/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public abstract class MvcProjectStructureDetector extends ProjectStructureDetector {
  private final MvcFramework myFramework;
  private final String myDirectoryName;

  public MvcProjectStructureDetector(MvcFramework framework) {
    myFramework = framework;
    myDirectoryName = myFramework.getFrameworkName().toLowerCase() + "-app";
  }

  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir, @NotNull File[] children, @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    for (File child : children) {
      if (child.getName().equals("build.gradle")) return DirectoryProcessingResult.PROCESS_CHILDREN;
    }
    for (File child : children) {
      if (child.getName().equals(myDirectoryName) && child.isDirectory()) {
        result.add(new GroovyMvcProjectRoot(dir));
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor,
                                                  Icon stepIcon) {
    final ModuleWizardStep groovySdkStep = new GroovySdkForProjectFromSourcesStep(this, builder, projectDescriptor, myFramework);
    final ModuleWizardStep javaSdkStep = ProjectWizardStepFactory.getInstance().createProjectJdkStep(builder.getContext());
    return Arrays.asList(javaSdkStep, groovySdkStep);
  }

  private class GroovyMvcProjectRoot extends DetectedProjectRoot {
    public GroovyMvcProjectRoot(File dir) {
      super(dir);
    }

    @NotNull
    @Override
    public String getRootTypeName() {
      return myFramework.getDisplayName();
    }

    @Override
    public boolean canContainRoot(@NotNull DetectedProjectRoot root) {
      return false;
    }
  }
}
