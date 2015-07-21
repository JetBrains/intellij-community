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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.List;

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public class GradleDistributionsContributor implements GradleMethodContextContributor {

  static final String DISTRIBUTIONS = "distributions";
  private static final String CONTENTS_METHOD = "contents";

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

    if (methodCallInfo.size() > 1 && DISTRIBUTIONS.equals(place.getText()) && place instanceof GrReferenceExpressionImpl) {
      GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, GRADLE_API_DISTRIBUTION_CONTAINER);
    }

    if (methodCallInfo.size() > 1 && methodCall.equals("project")) {
      methodCallInfo.remove(methodCallInfo.size() - 1);
      methodCall = ContainerUtil.getLastItem(methodCallInfo);
    }

    if (methodCall == null || !StringUtil.startsWith(methodCall, DISTRIBUTIONS)) {
      return;
    }

    String closureMethod = null;
    String configureClosureClazz = null;
    String contributorClass = null;
    boolean isRootRelated = StringUtil.startsWith(methodCall, DISTRIBUTIONS + '.');

    if (methodCallInfo.size() == 1) {
      configureClosureClazz = GRADLE_API_DISTRIBUTION_CONTAINER;
      if (place instanceof GrReferenceExpressionImpl) {
        String varClazz =
          StringUtil.startsWith(methodCall, DISTRIBUTIONS + '.') ? GRADLE_API_DISTRIBUTION_CONTAINER : GRADLE_API_DISTRIBUTION;
        GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, varClazz);
      }
      else {
        contributorClass = GRADLE_API_DISTRIBUTION_CONTAINER;
      }
      closureMethod = "configure";
    }
    else if (methodCallInfo.size() == 2) {
      configureClosureClazz = GRADLE_API_DISTRIBUTION;
      contributorClass = GRADLE_API_DISTRIBUTION;
      closureMethod = "create";
    }
    else if (methodCallInfo.size() == 3 && CONTENTS_METHOD.equals(place.getText())) {
      GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      PsiClass psiClass = psiManager.findClassWithCache(GRADLE_API_DISTRIBUTION, place.getResolveScope());

      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil.createMethodWithClosure(CONTENTS_METHOD, GRADLE_API_FILE_COPY_SPEC, null, place, psiManager);
      if (methodWithClosure != null) {
        if (psiClass != null) {
          PsiMethod psiMethod = ArrayUtil.getFirstElement(psiClass.findMethodsByName(CONTENTS_METHOD, false));
          if (psiMethod != null) {
            methodWithClosure.setNavigationElement(psiMethod);
          }
        }
        processor.execute(methodWithClosure, state);
      }
    }
    else if (methodCallInfo.size() == 4 && CONTENTS_METHOD.equals(methodCallInfo.get(1))) {
      GradleResolverUtil.processDeclarations(methodCallInfo.get(0), GroovyPsiManager.getInstance(place.getProject()),
                                             processor, state, place, GRADLE_API_FILE_COPY_SPEC);
    }

    if (configureClosureClazz != null && !isRootRelated) {
      final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil.createMethodWithClosure(closureMethod, configureClosureClazz, null, place, psiManager);
      if (methodWithClosure != null) {
        processor.execute(methodWithClosure, state);
      }
    }

    if (contributorClass != null) {
      GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      GradleResolverUtil.processDeclarations(psiManager, processor, state, place, contributorClass);
    }
  }
}
