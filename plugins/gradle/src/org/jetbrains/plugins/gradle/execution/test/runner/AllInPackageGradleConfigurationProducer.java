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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author Vladislav.Soroka
 * @since 2/14/14
 */
public class AllInPackageGradleConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {

  private static final List<String> TASKS_TO_RUN = ContainerUtil.newArrayList("cleanTest", "test");

  public AllInPackageGradleConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {

    final PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;
    sourceElement.set(psiPackage);

    final Module module = context.getModule();
    if (module == null) return false;

    if (!StringUtil.equals(
      module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY),
      GradleConstants.SYSTEM_ID.toString())) {
      return false;
    }

    final String linkedGradleProject = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
    if (linkedGradleProject == null) return false;
    configuration.getSettings().setExternalProjectPath(linkedGradleProject);
    configuration.getSettings().setTaskNames(TASKS_TO_RUN);
    if (psiPackage.getQualifiedName().isEmpty()) {
      configuration.getSettings().setScriptParameters("--tests *");
    }
    else {
      configuration.getSettings()
        .setScriptParameters(String.format("--tests %s.*", psiPackage.getQualifiedName()));
    }
    configuration.setName(suggestName(psiPackage, module));
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    if (configuration == null) return false;
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;

    final PsiPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
    if (psiPackage == null) return false;

    if (context.getModule() == null) return false;

    if (!StringUtil.equals(
      context.getModule().getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY),
      configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    if (!configuration.getSettings().getTaskNames().containsAll(TASKS_TO_RUN)) return false;

    final String scriptParameters = configuration.getSettings().getScriptParameters() + ' ';
    return psiPackage.getQualifiedName().isEmpty()
           ? scriptParameters.contains("--tests * ")
           : scriptParameters.contains(String.format("--tests %s.* ", psiPackage.getQualifiedName()));
  }

  private static String suggestName(@NotNull PsiPackage aPackage, @NotNull Module module) {
    return aPackage.getQualifiedName().isEmpty()
           ? ExecutionBundle.message("test.in.scope.presentable.text", module.getName())
           : ExecutionBundle.message("test.in.scope.presentable.text", aPackage.getQualifiedName());
  }
}
