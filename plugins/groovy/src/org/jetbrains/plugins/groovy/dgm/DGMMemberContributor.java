/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class DGMMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    Project project = place.getProject();
    GlobalSearchScope resolveScope = place.getResolveScope();
    GroovyPsiManager groovyPsiManager = GroovyPsiManager.getInstance(project);

    Pair<List<String>, List<String>> extensions = GroovyExtensionProvider.getInstance(project).collectExtensions(resolveScope);

    List<String> instanceCategories = extensions.getFirst();
    List<String> staticCategories = extensions.getSecond();

    if (!processCategories(qualifierType, processor, state, project, resolveScope, groovyPsiManager, instanceCategories, false)) return;
    if (!processCategories(qualifierType, processor, state, project, resolveScope, groovyPsiManager, staticCategories, true)) return;
  }

  private static boolean processCategories(PsiType qualifierType,
                                           PsiScopeProcessor processor,
                                           ResolveState state,
                                           Project project,
                                           GlobalSearchScope resolveScope,
                                           GroovyPsiManager groovyPsiManager, List<String> instanceCategories,
                                           boolean isStatic) {
    for (String category : instanceCategories) {
      PsiClass clazz = groovyPsiManager.findClassWithCache(category, resolveScope);
      if (clazz != null) {
        if (!GdkMethodHolder.getHolderForClass(clazz, isStatic, resolveScope).processMethods(processor, state, qualifierType, project)) {
          return false;
        }
      }
    }
    return true;
  }
}
