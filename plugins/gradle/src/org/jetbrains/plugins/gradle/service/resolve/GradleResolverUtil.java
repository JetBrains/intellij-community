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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrImplicitVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public class GradleResolverUtil {

  public static int getGrMethodArumentsCount(@NotNull GrArgumentList args) {
    int argsCount = 0;
    boolean namedArgProcessed = false;
    for (GroovyPsiElement arg : args.getAllArguments()) {
      if (arg instanceof GrNamedArgument) {
        if (!namedArgProcessed) {
          namedArgProcessed = true;
          argsCount++;
        }
      }
      else {
        argsCount++;
      }
    }
    return argsCount;
  }

  public static void addImplicitVariable(@NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         @NotNull GrReferenceExpressionImpl expression,
                                         @NotNull String type) {
    if (expression.getQualifier() == null) {
      PsiVariable myPsi = new GrImplicitVariableImpl(expression.getManager(), expression.getReferenceName(), type, expression);
      processor.execute(myPsi, state);
    }
  }

  public static void addImplicitVariable(@NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         @NotNull PsiElement element,
                                         @NotNull String type) {
    PsiVariable myPsi = new GrImplicitVariableImpl(element.getManager(), element.getText(), type, element);
    processor.execute(myPsi, state);
  }


  public static GrLightMethodBuilder createMethodWithClosure(@NotNull String name, @NotNull PsiElement place, @Nullable String returnType) {
    GrLightMethodBuilder methodWithClosure = new GrLightMethodBuilder(place.getManager(), name);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
    PsiClassType closureType = factory.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, place.getResolveScope());
    GrLightParameter closureParameter = new GrLightParameter("closure", closureType, methodWithClosure);
    methodWithClosure.addParameter(closureParameter);

    PsiClassType retType = factory.createTypeByFQClassName(
      returnType != null ? returnType : CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope());
    methodWithClosure.setReturnType(retType);
    return methodWithClosure;
  }

  public static void processMethod(@NotNull String gradleConfigurationName,
                                   @NotNull PsiClass dependencyHandlerClass,
                                   @NotNull PsiScopeProcessor processor,
                                   @NotNull ResolveState state,
                                   @NotNull PsiElement place) {
    processMethod(gradleConfigurationName, dependencyHandlerClass, processor, state, place, null);
  }

  public static void processMethod(@NotNull String gradleConfigurationName,
                                   @NotNull PsiClass dependencyHandlerClass,
                                   @NotNull PsiScopeProcessor processor,
                                   @NotNull ResolveState state,
                                   @NotNull PsiElement place,
                                   @Nullable String defaultMethodName) {
    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), gradleConfigurationName);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
    PsiType type = new PsiArrayType(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
    builder.addParameter(new GrLightParameter("param", type, builder));
    PsiClassType retType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope());
    builder.setReturnType(retType);
    processor.execute(builder, state);

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return;
    }
    GrArgumentList args = call.getArgumentList();
    if (args == null) {
      return;
    }

    int argsCount = getGrMethodArumentsCount(args);
    argsCount++; // Configuration name is delivered as an argument.

    for (PsiMethod method : dependencyHandlerClass.findMethodsByName(gradleConfigurationName, false)) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        builder.setNavigationElement(method);
        return;
      }
    }

    if (defaultMethodName != null) {
      for (PsiMethod method : dependencyHandlerClass.findMethodsByName(defaultMethodName, false)) {
        if (method.getParameterList().getParametersCount() == argsCount) {
          builder.setNavigationElement(method);
          return;
        }
      }
    }
  }

  public static void processDeclarations(@NotNull GroovyPsiManager psiManager,
                                         @NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         @NotNull PsiElement place,
                                         @NotNull String... fqNames) {
    for (String fqName : fqNames) {
      PsiClass psiClass = psiManager.findClassWithCache(fqName, place.getResolveScope());
      if (psiClass != null) {
        psiClass.processDeclarations(processor, state, null, place);
      }
    }
  }
}
