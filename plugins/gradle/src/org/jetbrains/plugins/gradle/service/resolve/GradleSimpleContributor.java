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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public abstract class GradleSimpleContributor implements GradleMethodContextContributor {

  private final String blockName;
  private final String fqName;
  private final List<String> myMixIns;

  protected GradleSimpleContributor(@NotNull String blockName, @NotNull String fqName, String... mixIns) {
    this.blockName = blockName;
    this.fqName = fqName;
    myMixIns = ContainerUtil.newArrayList(mixIns);
  }

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty() || methodCallInfo.size() < 2 || !blockName.equals(methodCallInfo.get(1))) {
      return;
    }
    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    GradleResolverUtil.processDeclarations(psiManager, processor, state, place, fqName);
    for(final String mixin : myMixIns) {
      PsiClass contributorClass =
        psiManager.findClassWithCache(mixin, place.getResolveScope());
      if (contributorClass == null) continue;
      GradleResolverUtil.processMethod(methodCallInfo.get(0), contributorClass, processor, state, place);
    }
  }
}
