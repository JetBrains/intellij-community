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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author Max Medvedev
 */
public class GroovyRunnerUtil {

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
