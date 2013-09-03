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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
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

  public static GrLightMethodBuilder createMethodWithClosure(@NotNull String name, @NotNull PsiElement place, @Nullable String returnType) {
    GrLightMethodBuilder methodWithClosure = new GrLightMethodBuilder(place.getManager(), name);
    PsiElementFactory factory = JavaPsiFacade.getInstance(place.getManager().getProject()).getElementFactory();
    PsiClassType closureType = factory.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, place.getResolveScope());
    GrLightParameter closureParameter = new GrLightParameter("closure", closureType, methodWithClosure);
    methodWithClosure.addParameter(closureParameter);

    if (returnType != null) {
      PsiClassType retType = factory.createTypeByFQClassName(returnType, place.getResolveScope());
      methodWithClosure.setReturnType(retType);
    }
    return methodWithClosure;
  }
}
