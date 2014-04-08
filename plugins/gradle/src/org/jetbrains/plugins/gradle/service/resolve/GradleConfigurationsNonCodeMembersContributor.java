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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrImplicitVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION;
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public class GradleConfigurationsNonCodeMembersContributor extends NonCodeMembersContributor {

  private static final String METHOD_GET_BY_NAME = "getByName";

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (aClass == null) {
      return;
    }

    if (!GRADLE_API_CONFIGURATION_CONTAINER.equals(aClass.getQualifiedName())) {
      return;
    }

    // Assuming that the method call is equivalent to calling ConfigurationContainer.getByName()
    processConfigurationAddition(aClass, processor, state, place);
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void processConfigurationAddition(@NotNull PsiClass dependencyHandlerClass,
                                            @NotNull PsiScopeProcessor processor,
                                            @NotNull ResolveState state,
                                            @NotNull PsiElement place) {

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      // TODO replace with groovy implicit method
      if(place instanceof GrReferenceExpressionImpl) {
        GrReferenceExpressionImpl expression = (GrReferenceExpressionImpl)place;
        String expr = expression.getCanonicalText();
        GrImplicitVariableImpl myPsi = new GrImplicitVariableImpl(place.getManager(), expr, GRADLE_API_CONFIGURATION, place);
        processor.execute(myPsi, state);
        setNavigation(myPsi, dependencyHandlerClass, METHOD_GET_BY_NAME, 1);
      }
      return;
    }
    GrArgumentList args = call.getArgumentList();
    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);

    argsCount++; // Configuration name is delivered as an argument.

    if (argsCount == 1) {
      GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), METHOD_GET_BY_NAME);
      PsiClassType type = PsiType.getJavaLangObject(place.getManager(), place.getResolveScope());
      builder.addParameter(new GrLightParameter("s", type, builder));
      processor.execute(builder, state);

      argsCount++; // we need method with extra argument of type Closure.
      setNavigation(builder, dependencyHandlerClass, METHOD_GET_BY_NAME, argsCount);
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void setNavigation(LightElement element, PsiClass dependencyHandlerClass, String methodName, int parametersCount) {
    for (PsiMethod method : dependencyHandlerClass.findMethodsByName(methodName, false)) {
      int methodParameterCount = method.getParameterList().getParametersCount();
      if (methodParameterCount == parametersCount &&
          method.getParameterList().getParameters()[methodParameterCount - 1].getType().equalsToText(GROOVY_LANG_CLOSURE)) {
        element.setNavigationElement(method);
      }
    }
  }
}
