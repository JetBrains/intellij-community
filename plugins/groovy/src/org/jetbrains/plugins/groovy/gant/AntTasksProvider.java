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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.refactoring.psi.SearchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public class AntTasksProvider {

  @NonNls public static final String ANT_TASK_CLASS = "org.apache.tools.ant.Task";

  private final Project myProject;
  private final CachedValue<Set<LightMethodBuilder>> myCachedValue;

  public static AntTasksProvider getInstance(Project project) {
    return ServiceManager.getService(project, AntTasksProvider.class);
  }

  public AntTasksProvider(Project project) {
    myProject = project;
    final CachedValuesManager manager = CachedValuesManager.getManager(myProject);
    myCachedValue = manager.createCachedValue(new CachedValueProvider<Set<LightMethodBuilder>>() {
      public Result<Set<LightMethodBuilder>> compute() {
        final Set<LightMethodBuilder> set = findAntTasks(myProject);
        return Result.create(set, ProjectRootManager.getInstance(myProject), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    }, false);
  }
  
  public Set<LightMethodBuilder> getAntTasks() {
    return myCachedValue.getValue();
  }

  private static Set<LightMethodBuilder> findAntTasks(Project project) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass taskClass = facade.findClass(ANT_TASK_CLASS, GlobalSearchScope.allScope(project));

    if (taskClass != null) {
      final Set<LightMethodBuilder> classNames = new HashSet<LightMethodBuilder>();
      for (PsiClass inheritor : SearchUtils.findClassInheritors(taskClass, true)) {
        if (!inheritor.hasModifierProperty(PsiModifier.ABSTRACT) && !inheritor.hasModifierProperty(PsiModifier.PRIVATE)) {
          final String name = inheritor.getName();
          if (name != null) {
            final LightMethodBuilder taskMethod =
              new LightMethodBuilder(inheritor.getManager(), GroovyFileType.GROOVY_LANGUAGE, StringUtil.decapitalize(name)).
                setModifiers(PsiModifier.PUBLIC).
                addParameter("args", CommonClassNames.JAVA_UTIL_MAP).
                setNavigationElement(inheritor).setBaseIcon(GantIcons.ANT_TASK);
            final PsiType closureType = JavaPsiFacade.getElementFactory(project).createTypeFromText(GrClosableBlock.GROOVY_LANG_CLOSURE, taskMethod);
            final GrLightParameter bodyParameter = new GrLightParameter(taskMethod.getManager(), "body", null, closureType, taskMethod);
            classNames.add(taskMethod.addParameter(bodyParameter.setOptional(true)));
          }
        }
      }
      return classNames;
    }

    return Collections.emptySet();
  }

}
