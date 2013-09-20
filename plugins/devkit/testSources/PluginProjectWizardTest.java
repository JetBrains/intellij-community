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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

/**
 * @author Dmitry Avdeev
 *         Date: 11/8/12
 */
public class PluginProjectWizardTest extends ProjectWizardTestCase {

  public void testPluginProject() throws Exception {
    createSdk("devkit", IdeaJdk.getInstance());
    Project project = createProjectFromTemplate(JavaModuleType.JAVA_GROUP, PluginModuleType.getInstance().getName(), null);
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile virtualFile = VfsUtilCore.findRelativeFile("META-INF/plugin.xml", baseDir);
    assertNotNull(virtualFile);
  }

  public void testProjectWithoutSdk() throws Exception {
    try {
      createProjectFromTemplate(JavaModuleType.JAVA_GROUP, PluginModuleType.getInstance().getName(), null);
      fail("Exception should be thrown");
    }
    catch (Exception e) {
      assertEquals(IdeBundle.message("prompt.confirm.project.no.jdk"), e.getMessage());
    }
  }
}
