/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyNamedArgumentProvider {

  public static final ExtensionPointName<GroovyNamedArgumentProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.namedArgumentProvider");

  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    throw new UnsupportedOperationException();
  }

  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable GroovyResolveResult resolveResult,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    getNamedArguments(call, resolve, argumentName, forCompletion, result);
  }

  public static Map<String, NamedArgumentDescriptor> getNamedArgumentsFromAllProviders(@NotNull GrCall call,
                                                                                  @Nullable String argumentName,
                                                                                  boolean forCompletion) {
    Map<String, NamedArgumentDescriptor> namedArguments = new HashMap<String, NamedArgumentDescriptor>() {
      @Override
      public NamedArgumentDescriptor put(String key, NamedArgumentDescriptor value) {
        NamedArgumentDescriptor oldValue = super.put(key, value);
        if (oldValue != null) {
          super.put(key, oldValue);
        }

        //noinspection ConstantConditions
        return oldValue;
      }
    };

    GroovyResolveResult[] callVariants = call.getCallVariants(null);

    if (callVariants.length == 0) {
      for (GroovyNamedArgumentProvider namedArgumentProvider : EP_NAME.getExtensions()) {
        namedArgumentProvider.getNamedArguments(call, null, null, argumentName, forCompletion, namedArguments);
      }
    }
    else {
      for (GroovyResolveResult result : callVariants) {
        PsiElement element = result.getElement();
        if (element instanceof GrAccessorMethod) continue;

        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiParameter[] parameters = method.getParameterList().getParameters();

          if (!method.isConstructor() && !(parameters.length > 0 && canBeMap(parameters[0]))) continue;

          for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
            if (methodInfo.getNamedArguments() != null) {
              if (methodInfo.isApplicable(method)) {
                namedArguments.putAll(methodInfo.getNamedArguments());
              }
            }
            else if (methodInfo.isNamedArgumentProviderDefined()) {
              if (methodInfo.isApplicable(method)) {
                methodInfo.getNamedArgProvider().getNamedArguments(call, element, result, argumentName, forCompletion, namedArguments);
              }
            }
          }
        }

        for (GroovyNamedArgumentProvider namedArgumentProvider : EP_NAME.getExtensions()) {
          namedArgumentProvider.getNamedArguments(call, element, result, argumentName, forCompletion, namedArguments);
        }
      }
    }

    return namedArguments;
  }

  public static boolean canBeMap(PsiParameter parameter) {
    if (parameter instanceof GrParameter) {
      if (((GrParameter)parameter).getTypeElementGroovy() == null) return true;
    }
    return GroovyPsiManager.isInheritorCached(parameter.getType(), CommonClassNames.JAVA_UTIL_MAP);
  }
}
