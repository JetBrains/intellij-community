/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 12/19/2015
 */
public class PatternGradleConfigurationProducer extends GradleTestRunConfigurationProducer {

  private final AbstractPatternBasedConfigurationProducer myBaseConfigurationProducer;

  protected PatternGradleConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
    myBaseConfigurationProducer = new GradlePatternBasedConfigurationProducer(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                    ConfigurationContext context,
                                                    Ref<PsiElement> sourceElement) {
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;

    final LinkedHashSet<String> tests = new LinkedHashSet<>();
    final PsiElement element = myBaseConfigurationProducer.checkPatterns(context, tests);
    if (element == null) {
      return false;
    }
    sourceElement.set(element);

    String projectPath = null;

    Set<String> scriptParameters = ContainerUtil.newLinkedHashSet();
    Set<String> tasksToRun = ContainerUtil.newLinkedHashSet();
    final Project project = context.getProject();

    final List<String> resolvedTests = ContainerUtil.newArrayList();
    for (String test : tests) {
      final int i = StringUtil.indexOf(test, ",");
      String aClass = i < 0 ? test : test.substring(0, i);
      final PsiClass psiClass =
        JavaPsiFacade.getInstance(project).findClass(aClass, GlobalSearchScope.projectScope(project));
      if (psiClass == null) continue;
      final PsiFile psiFile = psiClass.getContainingFile();
      if (psiFile == null) continue;

      final Module moduleForFile = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(psiFile.getVirtualFile());
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, moduleForFile)) return false;
      projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(moduleForFile);
      if (projectPath == null) return false;

      ContainerUtil.addAllNotNull(tasksToRun, getTasksToRun(moduleForFile));

      final String method = i != -1 && test.length() > i + 1 ? test.substring(i + 1) : null;
      scriptParameters.add(TestMethodGradleConfigurationProducer.createTestFilter(aClass, method));

      resolvedTests.add(psiClass.getName() + "," + (method == null ? "" : method));
    }

    if (tasksToRun.isEmpty() || projectPath == null) return false;

    configuration.getSettings().setExternalProjectPath(projectPath);
    configuration.getSettings().setTaskNames(ContainerUtil.newArrayList(tasksToRun));
    configuration.getSettings().setScriptParameters(StringUtil.join(scriptParameters, " "));
    final String name;
    if (resolvedTests.size() > 1) {
      name = resolvedTests.get(0) + " and " + (resolvedTests.size() - 1) + " more";
    }
    else {
      name = StringUtil.join(tests, "|");
    }
    configuration.setName(name);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    return true;
  }

  @Override
  protected boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    return false;
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
    return myBaseConfigurationProducer.isMultipleElementsSelected(context);
  }

  private static class GradlePatternBasedConfigurationProducer extends AbstractPatternBasedConfigurationProducer {
    public GradlePatternBasedConfigurationProducer(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    protected boolean setupConfigurationFromContext(RunConfiguration configuration, ConfigurationContext context, Ref sourceElement) {
      return false;
    }

    @Override
    public boolean isConfigurationFromContext(RunConfiguration configuration, ConfigurationContext context) {
      return false;
    }
  }
}
