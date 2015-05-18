/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle;

import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.ide.projectWizard.ProjectTypeStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleModuleBuilder;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GradleProjectWizardTest extends NewProjectWizardTestCase {

  public void testGradleProject() throws Exception {
    final String projectName = "testProject";
    Project project = createProject(new Consumer<Step>() {
      @Override
      public void consume(Step step) {
        if (step instanceof ProjectTypeStep) {
          assertTrue(((ProjectTypeStep)step).setSelectedTemplate("Gradle", null));
          List<ModuleWizardStep> steps = myWizard.getSequence().getSelectedSteps();
          assertEquals(5, steps.size());
          final ProjectBuilder projectBuilder = myWizard.getProjectBuilder();
          assertInstanceOf(projectBuilder, GradleModuleBuilder.class);
          ((GradleModuleBuilder)projectBuilder).setName(projectName);
        }
      }
    });

    assertEquals(projectName, project.getName());
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(1, modules.length);
    final Module module = modules[0];
    assertTrue(ModuleRootManager.getInstance(module).isSdkInherited());
    assertEquals(projectName, module.getName());

    VirtualFile root = ProjectRootManager.getInstance(project).getContentRoots()[0];
    VirtualFile settingsScript = VfsUtilCore.findRelativeFile("settings.gradle", root);
    assertNotNull(settingsScript);
    assertEquals(String.format("rootProject.name = '%s'\n\n", projectName),
                 StringUtil.convertLineSeparators(VfsUtilCore.loadText(settingsScript)));

    VirtualFile buildScript = VfsUtilCore.findRelativeFile("build.gradle", root);
    assertNotNull(buildScript);
    assertEquals("group '" + projectName + "'\n" +
                 "version '1.0-SNAPSHOT'\n" +
                 "\n" +
                 "apply plugin: 'java'\n" +
                 "\n" +
                 "sourceCompatibility = 1.5\n" +
                 "\n" +
                 "repositories {\n" +
                 "    mavenCentral()\n" +
                 "}\n" +
                 "\n" +
                 "dependencies {\n" +
                 "    testCompile group: 'junit', name: 'junit', version: '4.11'\n" +
                 "}\n",
                 StringUtil.convertLineSeparators(VfsUtilCore.loadText(buildScript)));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configureJdk();
  }
}
