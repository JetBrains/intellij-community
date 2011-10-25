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

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.newProjectWizard.DetectedProjectRoot;
import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.newProjectWizard.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public abstract class MvcProjectStructureDetector extends ProjectStructureDetector {
  private final MvcFramework myFramework;

  public MvcProjectStructureDetector(MvcFramework framework) {
    myFramework = framework;
  }

  @NotNull
  @Override
  public List<DetectedProjectRoot> detectRoots(File dir) {
    final List<DetectedProjectRoot> result = new ArrayList<DetectedProjectRoot>();
    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isDirectory() && new File(file, myFramework.getFrameworkName().toLowerCase() + "-app").isDirectory()) {
          result.add(new DetectedProjectRoot(file) {
            @NotNull
            @Override
            public String getRootTypeName() {
              return myFramework.getDisplayName();
            }

            @Override
            public boolean canContainRoot(@NotNull DetectedProjectRoot root) {
              return false;
            }
          });
        }
        return true;
      }
    });
    return result;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor, WizardContext context,
                                                  Icon stepIcon) {
    final ModuleWizardStep groovySdkStep = new GroovySdkForProjectFromSourcesStep(this, builder, projectDescriptor, myFramework, context);
    final ModuleWizardStep javaSdkStep = ProjectWizardStepFactory.getInstance().createProjectJdkStep(context);
    return Arrays.asList(javaSdkStep, groovySdkStep);
  }
}
