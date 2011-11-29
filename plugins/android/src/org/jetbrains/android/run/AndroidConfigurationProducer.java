/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.run.testing.AndroidTestRunConfigurationType;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class AndroidConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {
  private PsiElement mySourceElement;

  public AndroidConfigurationProducer() {
    super(AndroidRunConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final Module module = context.getModule();
    if (module == null) return null;
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    PsiElement element = location.getPsiElement();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(element.getProject());
    GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(true);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, scope);
    if (activityClass == null) return null;

    PsiClass elementClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    while (elementClass != null) {
      if (elementClass.isInheritor(activityClass, true)) {
        mySourceElement = elementClass;
        return createRunActivityConfiguration((PsiClass)mySourceElement, context);
      }
      elementClass = PsiTreeUtil.getParentOfType(elementClass, PsiClass.class);
    }
    return null;
  }

  @Nullable
  private RunnerAndConfigurationSettings createRunActivityConfiguration(PsiClass psiClass, ConfigurationContext context) {
    Project project = psiClass.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);

    final AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
    final String classQName = psiClass.getQualifiedName();
    if (classQName == null) {
      return null;
    }

    configuration.ACTIVITY_CLASS = classQName;
    configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
    configuration.setName(JavaExecutionUtil.getPresentableClassName(configuration.ACTIVITY_CLASS, configuration.getConfigurationModule()));
    setupConfigurationModule(context, configuration);

    final TargetSelectionMode targetSelectionMode = AndroidUtils
      .getDefaultTargetSelectionMode(context.getModule(), AndroidRunConfigurationType.getInstance(),
                                     AndroidTestRunConfigurationType.getInstance());

    if (targetSelectionMode != null) {
      configuration.setTargetSelectionMode(targetSelectionMode);
    }
    
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}
