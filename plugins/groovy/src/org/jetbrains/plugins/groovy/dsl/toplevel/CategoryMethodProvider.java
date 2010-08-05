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
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class CategoryMethodProvider {
  private static final Key<CachedValue<MultiMap<String, PsiMethod>>> METHOD_KEY = Key.create("Category methods");

  private CategoryMethodProvider() {
  }

  public static List<PsiMethod> provideMethods(@NotNull PsiType psiType,
                                        final Project project,
                                        String className,
                                        GlobalSearchScope scope,
                                        final Function<PsiMethod, PsiMethod> converter) {
    final PsiClass categoryClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
    if (categoryClass == null) return Collections.emptyList();
    final MultiMap<String, PsiMethod> map = CachedValuesManager.getManager(project)
      .getCachedValue(categoryClass, METHOD_KEY, new CachedValueProvider<MultiMap<String, PsiMethod>>() {
        @Override
        public Result<MultiMap<String, PsiMethod>> compute() {
          MultiMap<String, PsiMethod> map = new MultiMap<String, PsiMethod>();
          for (PsiMethod m : categoryClass.getMethods()) {
            final PsiParameter[] params = m.getParameterList().getParameters();
            if (params.length == 0) continue;
            final PsiType parameterType = params[0].getType();
            PsiType targetType = TypeConversionUtil.erasure(parameterType);
            map.putValue(targetType.getCanonicalText(), converter.fun(m));
          }
          return Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
        }
      }, false);
    Set<String> superTypes = ResolveUtil.getAllSuperTypes(psiType, project).keySet();
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    for (String superType : superTypes) {
      result.addAll(map.get(superType));
    }
    return result;
  }
}
