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

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

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
    if (methodCallInfo.isEmpty()) {
      return;
    }

    int i = methodCallInfo.indexOf("dependencies");
    if (i != 1) {
      return;
    }

    // Assuming that the method call is addition of new dependency into configuration.
    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    PsiClass contributorClass =
      psiManager.findClassWithCache(GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER, place.getResolveScope());
    if (contributorClass == null) {
      return;
    }
    processDependencyAddition(methodCallInfo.get(0), contributorClass, processor, state, place);
  }

  private static void processDependencyAddition(@NotNull String gradleConfigurationName,
                                                @NotNull PsiClass dependencyHandlerClass,
                                                @NotNull PsiScopeProcessor processor,
                                                @NotNull ResolveState state,
                                                @NotNull PsiElement place) {
    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), gradleConfigurationName);
    PsiElementFactory factory = JavaPsiFacade.getInstance(place.getManager().getProject()).getElementFactory();
    PsiType type = new PsiArrayType(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
    builder.addParameter(new GrLightParameter("dependencyInfo", type, builder));
    processor.execute(builder, state);

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return;
    }
    GrArgumentList args = call.getArgumentList();
    if (args == null) {
      return;
    }

    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);
    argsCount++; // Configuration name is delivered as an argument.

    for (PsiMethod method : dependencyHandlerClass.findMethodsByName("add", false)) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        builder.setNavigationElement(method);
      }
    }
  }
}
