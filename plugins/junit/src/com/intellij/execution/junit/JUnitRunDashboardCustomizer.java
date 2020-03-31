// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnitRunDashboardCustomizer extends RunDashboardCustomizer {
  @Override
  public boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    return settings.getConfiguration() instanceof JUnitConfiguration;
  }

  @Override
  @Nullable
  public PsiElement getPsiElement(@NotNull RunDashboardRunConfigurationNode node) {
    RunConfiguration runConfiguration = node.getConfigurationSettings().getConfiguration();
    if (!(runConfiguration instanceof JUnitConfiguration)) return null;

    JUnitConfiguration jUnitConfiguration = (JUnitConfiguration)runConfiguration;

    String runClassName = jUnitConfiguration.getRunClass();
    if (runClassName == null) return null;

    PsiClass runClass = jUnitConfiguration.getConfigurationModule().findClass(runClassName);
    if (runClass == null) return null;

    String testMethod = jUnitConfiguration.getPersistentData().getMethodName();
    if (testMethod != null) {
      PsiMethod[] methods = runClass.findMethodsByName(testMethod, false);
      if (methods.length > 0) {
        return methods[0];
      }
    }
    return runClass;
  }
}
