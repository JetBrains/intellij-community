/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.refactoring.psi.SearchUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class AntTasksProvider {

  @NonNls public static final String ANT_TASK_CLASS = "org.apache.tools.ant.Task";

  private final Project myProject;
  private final CachedValue<Map<String, PsiClass>> myCachedValue;

  public static AntTasksProvider getInstance(Project project) {
    return ServiceManager.getService(project, AntTasksProvider.class);
  }

  public AntTasksProvider(Project project) {
    myProject = project;
    final CachedValuesManager manager = CachedValuesManager.getManager(myProject);
    myCachedValue = manager.createCachedValue(new CachedValueProvider<Map<String, PsiClass>>() {
      public Result<Map<String, PsiClass>> compute() {
        final Map<String, PsiClass> set = findAntTasks(myProject);
        return Result.create(set, ProjectRootManager.getInstance(myProject), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    }, false);
  }
  
  public Map<String, PsiClass> getAntTasks() {
    return myCachedValue.getValue();
  }

  private static Map<String, PsiClass> findAntTasks(Project project) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass taskClass = facade.findClass(ANT_TASK_CLASS, GlobalSearchScope.allScope(project));

    if (taskClass != null) {
      final Map<String, PsiClass> classNames = new HashMap<String, PsiClass>();
      for (PsiClass inheritor : SearchUtils.findClassInheritors(taskClass, true)) {
        if (!inheritor.hasModifierProperty(PsiModifier.ABSTRACT) && !inheritor.hasModifierProperty(PsiModifier.PRIVATE)) {
          ContainerUtil.putIfNotNull(inheritor.getName(), inheritor, classNames);
        }
      }
      return classNames;
    }

    return Collections.emptyMap();
  }
}
