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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiSuperMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class JavaOverridingMethodUtil {
  private static final int MAX_OVERRIDDEN_METHOD_SEARCH = 20;

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
    PsiMethod[] methods =
      StubIndex.getElements(JavaStubIndexKeys.METHODS, name, project, effectiveSearchScope, PsiMethod.class)
        .stream()
        .filter(m -> m != method)
        .filter(preFilter)
        .limit(MAX_OVERRIDDEN_METHOD_SEARCH + 1)
        .toArray(PsiMethod[]::new);

    // search should be deterministic
    if (methods.length > MAX_OVERRIDDEN_METHOD_SEARCH) {
      return null;
    }

    return Stream.of(methods).filter(candidate -> PsiSuperMethodUtil.isSuperMethod(candidate, method));
  }
}
