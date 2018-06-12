/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

/**
 * @author Dmitry Avdeev
 */
public class PluginProjectWizardTest extends NewProjectWizardTestCase {

  public void testPluginProject() throws Exception {
    createSdk("devkit", IdeaJdk.getInstance());
    Project project = createProjectFromTemplate(PluginModuleType.getInstance().getName(), null, null);
    VirtualFile virtualFile = project.getBaseDir().findFileByRelativePath("resources/META-INF/plugin.xml");
    assertNotNull(virtualFile);

    RunnerAndConfigurationSettings configuration = RunManager.getInstance(project).getSelectedConfiguration();
    assertNotNull(configuration);
    ConfigurationType type = configuration.getType();
    assertNotNull(type);
    assertEquals(DevKitBundle.message("run.configuration.title"), type.getDisplayName());

    VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
    assertEquals(1, files.length);
  }
}
