// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

/**
 * @author Dmitry Avdeev
 */
public class PluginProjectWizardTest extends NewProjectWizardTestCase {
  public void testPluginProject() throws Exception {
    createSdk("devkit", IdeaJdk.getInstance());
    Project project = createProjectFromTemplate(PluginModuleType.getInstance().getName(), null, null);
    VirtualFile virtualFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findFileByRelativePath("resources/META-INF/plugin.xml");
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
