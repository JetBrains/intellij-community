// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil;
import org.jetbrains.plugins.gradle.util.TasksToRun;

/**
 * @author Vladislav.Soroka
 */
public final class AllInPackageGradleConfigurationProducer extends GradleTestRunConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
  }

  @Override
  protected boolean doSetupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                    ConfigurationContext context,
                                                    Ref<PsiElement> sourceElement) {
    final PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;
    sourceElement.set(psiPackage);

    final Module module = context.getModule();
    if (module == null) return false;

    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (projectPath == null) return false;

    PsiDirectory[] sourceDirs = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
    if (sourceDirs.length == 0) return false;
    VirtualFile source = sourceDirs[0].getVirtualFile();
    TasksToRun tasksToRun = findTestsTaskToRun(source, context.getProject());
    if (tasksToRun.isEmpty()) return false;

    configuration.getSettings().setExternalProjectPath(projectPath);
    configuration.getSettings().setTaskNames(tasksToRun);
    String filter = GradleExecutionSettingsUtil.createTestFilterFrom(psiPackage, /*hasSuffix=*/false);
    configuration.getSettings().setScriptParameters(filter);
    configuration.setName(suggestName(psiPackage, module));
    return true;
  }

  @Override
  protected boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    final PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;

    if (context.getModule() == null) return false;

    if (!StringUtil.equals(
      ExternalSystemApiUtil.getExternalProjectPath(context.getModule()),
      configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    Module module = context.getModule();
    PsiDirectory[] sourceDirs = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
    if (sourceDirs.length == 0) return false;
    VirtualFile source = sourceDirs[0].getVirtualFile();
    if (!hasTasksInConfiguration(source, context.getProject(), configuration.getSettings())) return false;

    final String scriptParameters = configuration.getSettings().getScriptParameters() + ' ';
    final String filter = GradleExecutionSettingsUtil.createTestFilterFrom(psiPackage, /*hasSuffix=*/true);
    return scriptParameters.contains(filter);
  }

  private static String suggestName(@NotNull PsiPackage aPackage, @NotNull Module module) {
    return aPackage.getQualifiedName().isEmpty()
           ? ExecutionBundle.message("test.in.scope.presentable.text", module.getName())
           : ExecutionBundle.message("test.in.scope.presentable.text", aPackage.getQualifiedName());
  }
}
