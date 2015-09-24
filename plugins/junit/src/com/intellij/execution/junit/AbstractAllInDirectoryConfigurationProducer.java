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

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.jps.model.java.JavaSourceRootType;


public abstract class AbstractAllInDirectoryConfigurationProducer extends JUnitConfigurationProducer {

  protected AbstractAllInDirectoryConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(JUnitConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final Project project = configuration.getProject();
    final PsiElement element = context.getPsiLocation();
    if (!(element instanceof PsiDirectory)) return false;
    final PsiPackage aPackage = JavaRuntimeConfigurationProducerBase.checkPackage(element);
    if (aPackage == null) return false;
    final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return false;
    if (!ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile)) return false;
    int testRootCount = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).size();
    if (testRootCount < 2) return false;
    if (!LocationUtil.isJarAttached(context.getLocation(), aPackage, JUnitUtil.TESTCASE_CLASS)) return false;
    setupConfigurationModule(context, configuration);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setDirName(virtualFile.getPath());
    data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY;
    configuration.setGeneratedName();
    return true;
  }
}
