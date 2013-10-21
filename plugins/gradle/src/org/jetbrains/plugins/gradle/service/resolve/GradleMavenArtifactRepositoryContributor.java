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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.List;

/**
 * @{GradleMavenArtifactRepositoryContributor} provides gradle MavenArtifactRepository DSL resolving contributor.
 *
 * e.g.
 * repositories {
 *    maven {
 *      url "http://snapshots.repository.codehaus.org/"
 *    }
 * }
 *
 * @author Vladislav.Soroka
 * @since 10/21/13
 */
public class GradleMavenArtifactRepositoryContributor implements GradleMethodContextContributor {

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty() || methodCallInfo.size() < 3 ||
        !"repositories".equals(methodCallInfo.get(2)) || !"maven".equals(methodCallInfo.get(1))) {
      return;
    }
    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    GradleResolverUtil.processDeclarations(
      psiManager, processor, state, place,
      GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY);
    PsiClass contributorClass = psiManager.findClassWithCache(
      GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY, place.getResolveScope());
    if (contributorClass == null) return;
    GradleResolverUtil.processMethod(methodCallInfo.get(0), contributorClass, processor, state, place);
  }
}
