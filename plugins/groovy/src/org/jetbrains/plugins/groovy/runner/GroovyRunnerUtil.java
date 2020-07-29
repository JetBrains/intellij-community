// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author Max Medvedev
 */
public final class GroovyRunnerUtil {

  public static String getConfigurationName(PsiClass aClass, RunConfigurationModule module) {
    String qualifiedName = aClass.getQualifiedName();
    Project project = module.getProject();
    if (qualifiedName == null) {
      return module.getModuleName();
    }

    PsiClass psiClass =
      JavaPsiFacade.getInstance(project).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.projectScope(project));
    if (psiClass != null) {
      return psiClass.getName();
    }
    else {
      int lastDot = qualifiedName.lastIndexOf('.');
      if (lastDot == -1 || lastDot == qualifiedName.length() - 1) {
        return qualifiedName;
      }
      return qualifiedName.substring(lastDot + 1);
    }
  }
}
