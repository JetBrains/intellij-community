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

package com.intellij.execution.junit;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;


public class AllInDirectoryConfigurationProducer extends JUnitConfigurationProducer {
  private PsiDirectory myDir = null;

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    if (!(element instanceof PsiDirectory)) return null;
    final PsiPackage aPackage = checkPackage(element);
    if (aPackage == null) return null;
    final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return null;
    final ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
    int testRootCount = 0;
    for (ContentEntry entry : entries) {
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (sourceFolder.isTestSource()) {
          testRootCount++;
          if (testRootCount > 1) {
            break;
          }
        }
      }
    }
    if (testRootCount < 2) return null;
    myDir = (PsiDirectory)element;
    if (!LocationUtil.isJarAttached(location, aPackage, JUnitUtil.TESTCASE_CLASS)) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setDirName(virtualFile.getPath());
    data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY;
    configuration.setGeneratedName();
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myDir;
  }
}
