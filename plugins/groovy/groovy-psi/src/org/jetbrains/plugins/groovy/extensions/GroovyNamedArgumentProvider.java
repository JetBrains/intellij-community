// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.EmptyGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an extension point for adding named arguments to method calls.
 * A named argument is an argument of the form {@code foo(bar: 42)}
 */

public abstract class GroovyNamedArgumentProvider {

  public static final ExtensionPointName<GroovyNamedArgumentProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.namedArgumentProvider");

  /**
   * Computes named arguments for a call.
   * The results are computed for a particular {@code resolveResult}, since for one call there may be multiple overload resolutions.
   * @param argumentName the supposed name of the named argument. Might be {@code null} if used for type-checking or completion.
   * @param result the accumulator of the results.
   */
  public void getNamedArguments(@NotNull GrCall call,
                                @NotNull GroovyResolveResult resolveResult,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                @NotNull Map<String, NamedArgumentDescriptor> result) {
  }

  public @NotNull Map<String, NamedArgumentDescriptor> getNamedArguments(@NotNull GrListOrMap literal) {
    return Collections.emptyMap();
  }

  public static @Nullable Map<String, NamedArgumentDescriptor> getNamedArgumentsFromAllProviders(@NotNull GrCall call,
                                                                                                 @Nullable String argumentName,
                                                                                                 boolean forCompletion) {
    Map<String, NamedArgumentDescriptor> namedArguments = new HashMap<>() {
      @Override
      public NamedArgumentDescriptor put(String key, NamedArgumentDescriptor value) {
        NamedArgumentDescriptor oldValue = super.put(key, value);
        if (oldValue != null) {
          super.put(key, oldValue);
        }

        return oldValue;
      }
    };

    GroovyResolveResult[] callVariants = call.getCallVariants(null);

    if (callVariants.length == 0 || PsiUtil.isSingleBindingVariant(callVariants)) {
      for (GroovyNamedArgumentProvider namedArgumentProvider : EP_NAME.getExtensions()) {
        namedArgumentProvider.getNamedArguments(call, EmptyGroovyResolveResult.INSTANCE, argumentName, forCompletion, namedArguments);
      }
    }
    else {
      boolean mapExpected = false;
      for (GroovyResolveResult result : callVariants) {
        for (GroovyNamedArgumentProvider namedArgumentProvider : EP_NAME.getExtensions()) {
          namedArgumentProvider.getNamedArguments(call, result, argumentName, forCompletion, namedArguments);
        }
        PsiElement element = result.getElement();
        if (element instanceof GrAccessorMethod) continue;

        if (element instanceof PsiMethod method) {
          PsiParameter[] parameters = method.getParameterList().getParameters();

          if (!method.isConstructor() && !(parameters.length > 0 && canBeMap(parameters[0]))) continue;

          mapExpected = true;

          for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
            if (methodInfo.getNamedArguments() != null || methodInfo.isNamedArgumentProviderDefined()) {
              if (methodInfo.isApplicable(method)) {
                if (methodInfo.isNamedArgumentProviderDefined()) {
                  methodInfo.getNamedArgProvider().getNamedArguments(call, result, argumentName, forCompletion, namedArguments);
                }
                if (methodInfo.getNamedArguments() != null) {
                  namedArguments.putAll(methodInfo.getNamedArguments());
                }
              }
            }
          }
        }

        if (element instanceof GrVariable &&
            InheritanceUtil.isInheritor(((GrVariable)element).getTypeGroovy(), GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
          mapExpected = true;
        }
      }
      if (!mapExpected && namedArguments.isEmpty()) {
        return null;
      }
    }

    return namedArguments;
  }

  private static boolean canBeMap(PsiParameter parameter) {
    PsiType type = parameter.getType();
    if (parameter instanceof GrParameter &&
        type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
        ((GrParameter)parameter).getTypeElementGroovy() == null) {
      return true;
    }
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }
}
