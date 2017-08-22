/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JavaOverridingMethodUtil {
  private static final int MAX_OVERRIDDEN_METHOD_SEARCH = 20;

  /**
   * The method allows to search of overriding methods in "cheap enough" manner if it's possible.
   *
   * @param preFilter should filter out non-interesting methods (eg: methods non-annotated with some '@Override' annotation).
   *                  It must not perform resolve, index queries or any other heavyweight operation since in the worst case all methods with name
   *                  as the source method name will be processed.
   *
   * @return null if it's expensive to search for overriding methods in given case, otherwise returns stream of overriding methods for given preFilter
   */
  @Nullable
  public static Stream<PsiMethod> getOverridingMethodsIfCheapEnough(@NotNull PsiMethod method,
                                                                    @Nullable GlobalSearchScope searchScope,
                                                                    @NotNull Predicate<PsiMethod> preFilter) {
    Project project = method.getProject();
    String name = method.getName();
    SearchScope useScope = method.getUseScope();
    GlobalSearchScope effectiveSearchScope = GlobalSearchScopeUtil.toGlobalSearchScope(useScope, project);
    if (searchScope != null) {
      effectiveSearchScope = effectiveSearchScope.intersectWith(searchScope);
    }

    List<PsiMethod> methods = ContainerUtil.newArrayList();
    if (!StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS,
                                                name,
                                                project,
                                                effectiveSearchScope,
                                                PsiMethod.class,
                                            m -> {
                                              ProgressManager.checkCanceled();
                                              if (m == method) return true;
                                              if (!preFilter.test(m)) return true;
                                              methods.add(m);
                                              return methods.size() <= MAX_OVERRIDDEN_METHOD_SEARCH;
                                            })) {
      return null;
    }

    return methods.stream().filter(candidate -> PsiSuperMethodUtil.isSuperMethod(candidate, method));
  }
}
