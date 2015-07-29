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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/13
 */
public class GradleSourceSetsContributor implements GradleMethodContextContributor {
  static final String SOURCE_SETS = "sourceSets";
  private static final String CONFIGURE_CLOSURE_METHOD = "configure";
  private static final int SOURCE_SET_CONTAINER_LEVEL = 1;
  private static final int SOURCE_SET_LEVEL = 2;
  private static final int SOURCE_DIRECTORY_LEVEL = 3;
  private static final int SOURCE_DIRECTORY_CLOSURE_LEVEL = 4;

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty()) {
      return;
    }
    String methodCall = ContainerUtil.getLastItem(methodCallInfo);
    if (methodCall == null) {
      return;
    }

    if (methodCallInfo.size() > 1 && SOURCE_SETS.equals(place.getText()) && place instanceof GrReferenceExpressionImpl) {
      GradleResolverUtil
        .addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER);
    }

    if (methodCallInfo.size() > 1 && methodCall.equals("project")) {
      methodCallInfo.remove(methodCallInfo.size() - 1);
      methodCall = ContainerUtil.getLastItem(methodCallInfo);
    }

    if (methodCall == null || methodCallInfo.size() > SOURCE_DIRECTORY_CLOSURE_LEVEL || !StringUtil.startsWith(methodCall, SOURCE_SETS)) {
      return;
    }

    String configureClosureClazz = null;
    String contributorClass = null;

    boolean isRootRelated = StringUtil.startsWith(methodCall, SOURCE_SETS + '.');

    if (methodCallInfo.size() == SOURCE_SET_CONTAINER_LEVEL) {
      configureClosureClazz = GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER;
      if (place instanceof GrReferenceExpressionImpl) {
        String varClazz = StringUtil.startsWith(methodCall, SOURCE_SETS + '.')
                          ? GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER
                          : GradleCommonClassNames.GRADLE_API_SOURCE_SET;
        GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, varClazz);
      }
      else {
        contributorClass = GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER;
      }
    }
    else if (methodCallInfo.size() == SOURCE_SET_LEVEL) {
      configureClosureClazz = GradleCommonClassNames.GRADLE_API_SOURCE_SET;
      contributorClass = GradleCommonClassNames.GRADLE_API_SOURCE_SET;
    }
    else if (methodCallInfo.size() == SOURCE_DIRECTORY_LEVEL) {
      GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      PsiClass psiClass = psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_SOURCE_SET, place.getResolveScope());
      configureClosureClazz =
        GradleResolverUtil.canBeMethodOf(place.getText(), psiClass) ? null : GradleCommonClassNames.GRADLE_API_SOURCE_DIRECTORY_SET;
      contributorClass = GradleCommonClassNames.GRADLE_API_SOURCE_DIRECTORY_SET;
    }
    else if (methodCallInfo.size() == SOURCE_DIRECTORY_CLOSURE_LEVEL) {
      contributorClass = GradleCommonClassNames.GRADLE_API_SOURCE_DIRECTORY_SET;
    }

    if (configureClosureClazz != null && !isRootRelated) {
      final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil
          .createMethodWithClosure(CONFIGURE_CLOSURE_METHOD, configureClosureClazz, null, place, psiManager);
      if (methodWithClosure != null) {
        processor.execute(methodWithClosure, state);
      }
    }
    //else {
    //  GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    //  GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GradleCommonClassNames.GRADLE_API_PROJECT);
    //}
    if (contributorClass != null) {
      GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      GradleResolverUtil.processDeclarations(psiManager, processor, state, place, contributorClass);
    }
  }
}
