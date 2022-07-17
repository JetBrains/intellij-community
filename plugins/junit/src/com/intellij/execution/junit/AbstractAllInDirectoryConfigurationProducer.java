// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

public class AbstractAllInDirectoryConfigurationProducer extends JUnitConfigurationProducer {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated(forRemoval = true)
  protected AbstractAllInDirectoryConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  public AbstractAllInDirectoryConfigurationProducer() {
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return JUnitConfiguration.TEST_DIRECTORY.equals(type);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final Project project = configuration.getProject();
    final PsiElement element = context.getPsiLocation();
    if (!(element instanceof PsiDirectory)) return false;
    final PsiPackage aPackage = AbstractJavaTestConfigurationProducer.checkPackage(element);
    if (aPackage == null) return false;
    final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return false;
    if (!ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile)) return false;
    int testRootCount = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).size();
    if (testRootCount < 2) return false;
    if (!LocationUtil.isJarAttached(context.getLocation(), aPackage, JUnitUtil.TEST_CASE_CLASS, JUnitUtil.TEST5_ANNOTATION,
                                    JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) return false;
    setupConfigurationModule(context, configuration);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setDirName(virtualFile.getPath());
    data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY;
    configuration.setGeneratedName();
    return true;
  }
}
