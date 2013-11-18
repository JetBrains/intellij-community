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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 8/14/13 12:58 PM
 */
public class GradleDependenciesContributor implements GradleMethodContextContributor {

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty()) return;

    String methodCall = ContainerUtil.getLastItem(methodCallInfo);
    if (methodCall == null) return;

    if (!StringUtil.equals(methodCall, "dependencies")) return;

    final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    if (methodCallInfo.size() == 2) {
      GradleResolverUtil.processDeclarations(
        psiManager, processor, state, place, GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY);
      // Assuming that the method call is addition of new dependency into configuration.
      PsiClass contributorClass =
        psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER, place.getResolveScope());
      if (contributorClass == null) return;
      GradleResolverUtil.processMethod(methodCallInfo.get(0), contributorClass, processor, state, place, "add");
    }
    else if (methodCallInfo.size() == 3) {
      GradleResolverUtil.processDeclarations(psiManager, processor, state, place,
                                             GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER,
                                             GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY,
                                             GradleCommonClassNames.GRADLE_API_ARTIFACTS_DEPENDENCY_ARTIFACT,
                                             GradleCommonClassNames.GRADLE_API_PROJECT);
    }
    else if (methodCallInfo.size() == 4) {
      // Assuming that the method call is addition of new dependency into configuration.
      PsiClass contributorClass =
        psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER, place.getResolveScope());
      if (contributorClass == null) return;
      GradleResolverUtil.processMethod(methodCallInfo.get(0), contributorClass, processor, state, place, "add");
    }
  }
}
