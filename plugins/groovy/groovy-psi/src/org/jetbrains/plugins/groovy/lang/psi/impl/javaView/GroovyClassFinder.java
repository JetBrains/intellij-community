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

package org.jetbrains.plugins.groovy.lang.psi.impl.javaView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GroovyClassFinder extends PsiElementFinder {
  private final GroovyShortNamesCache myCache;

  public GroovyClassFinder(Project project) {
    myCache = GroovyShortNamesCache.getGroovyShortNamesCache(project);
  }

  @Override
  @Nullable
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final List<PsiClass> classes = myCache.getClassesByFQName(qualifiedName, scope, true);
    if (classes.isEmpty()) return null;
    if (classes.size() == 1) return classes.get(0);
    return Collections.min(classes, PsiClassUtil
      .createScopeComparator(scope)
      .thenComparing(c -> c.getQualifiedName(), Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(c -> {
        PsiFile file = c.getContainingFile();
        return file instanceof GroovyFileBase ? ((GroovyFileBase)file).getPackageName() : "";
      }));
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final Collection<PsiClass> classes = myCache.getClassesByFQName(qualifiedName, scope, true);
    return classes.isEmpty() ? PsiClass.EMPTY_ARRAY : classes.toArray(PsiClass.EMPTY_ARRAY);
  }

}
