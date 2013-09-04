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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/13
 */
public class GradleConfigurationsContributor implements GradleMethodContextContributor {

  private static final String CONFIGURATIONS = "configurations";

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty()) {
      return;
    }
    final String methodCall = methodCallInfo.get(0);

    PsiClass contributorClass = null;
    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    if (methodCallInfo.size() == 1) {
      if (methodCall.startsWith(CONFIGURATIONS + '.')) {
        contributorClass = psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_CONFIGURATION, place.getResolveScope());
      }
      else if (CONFIGURATIONS.equals(methodCall)) {
        contributorClass = psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER, place.getResolveScope());
        if (place instanceof GrReferenceExpressionImpl) {
          GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, GradleCommonClassNames.GRADLE_API_CONFIGURATION);
          return;
        }
      }
    }
    else if (methodCallInfo.size() == 2) {
      if (CONFIGURATIONS.equals(methodCallInfo.get(1))) {
        contributorClass = psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER, place.getResolveScope());
        if (place instanceof GrReferenceExpressionImpl) {
          GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, GradleCommonClassNames.GRADLE_API_CONFIGURATION);
          return;
        }
      }
    }
    if (contributorClass != null) {
      contributorClass.processDeclarations(processor, state, null, place);
    }
  }
}
