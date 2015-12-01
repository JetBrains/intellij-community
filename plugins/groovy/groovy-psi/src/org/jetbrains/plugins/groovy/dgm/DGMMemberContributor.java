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
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides members from extension classes referenced in {@code META-INF/services/org.codehaus.groovy.runtime.ExtensionModule}.
 */
public class DGMMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return;

    final Project project = place.getProject();

    ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>> map = CachedValuesManager.getManager(project).getCachedValue(
      project, new CachedValueProvider<ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>>>() {
        @Nullable
        @Override
        public Result<ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>>> compute() {
          ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>> value = ContainerUtil.createConcurrentSoftValueMap();
          return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
        }
      });

    GlobalSearchScope scope = place.getResolveScope();
    List<GdkMethodHolder> gdkMethods = map.get(scope);
    if (gdkMethods == null) {
      map.put(scope, gdkMethods = calcGdkMethods(project, scope));
    }

    for (GdkMethodHolder holder : gdkMethods) {
      if (!holder.processMethods(processor, state, qualifierType, project)) {
        return;
      }
    }
  }

  @NotNull
  private static List<GdkMethodHolder> calcGdkMethods(Project project, GlobalSearchScope resolveScope) {
    List<GdkMethodHolder> gdkMethods = ContainerUtil.newArrayList();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    Couple<List<String>> extensions = GroovyExtensionProvider.getInstance(project).collectExtensions(resolveScope);
    for (String category : extensions.getFirst()) {
      PsiClass clazz = facade.findClass(category, resolveScope);
      if (clazz != null) {
        gdkMethods.add(GdkMethodHolder.getHolderForClass(clazz, false, resolveScope));
      }
    }
    for (String category : extensions.getSecond()) {
      PsiClass clazz = facade.findClass(category, resolveScope);
      if (clazz != null) {
        gdkMethods.add(GdkMethodHolder.getHolderForClass(clazz, true, resolveScope));
      }
    }
    return gdkMethods;
  }
}
