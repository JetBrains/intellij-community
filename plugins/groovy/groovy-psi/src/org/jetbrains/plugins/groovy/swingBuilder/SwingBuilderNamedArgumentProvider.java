// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.swingBuilder;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.impl.TypeCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SwingBuilderNamedArgumentProvider extends GroovyNamedArgumentProvider {

  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @NotNull GroovyResolveResult resolveResult,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                @NotNull Map<String, NamedArgumentDescriptor> result) {
    PsiElement resolve = resolveResult.getElement();
    PsiType returnType = resolve == null ? null : ((PsiMethod)resolve).getReturnType();
    PsiClass aClass = PsiTypesUtil.getPsiClass(returnType);
    if (aClass == null) return;

    Map<String, Pair<PsiType, PsiElement>> typeMap = null;
    if (!forCompletion) {
      typeMap = new HashMap<>();
    }

    PsiManager manager = aClass.getManager();

    for (PsiMethod method : aClass.getAllMethods()) {
      String methodName = method.getName();
      String propertyName = GroovyPropertyUtils.getPropertyNameBySetterName(methodName);
      if (propertyName != null) {
        if (argumentName != null && !argumentName.equals(propertyName)) continue;

        PsiType methodReturnType = method.getReturnType();
        if (methodReturnType != null && !PsiType.VOID.equals(methodReturnType)) continue;

        PsiParameter[] parameters = method.getParameterList().getParameters();

        if (parameters.length != 1) continue;

        if (forCompletion) { // optimization, don't calculate types.
          result.put(propertyName, NamedArgumentDescriptor.SIMPLE_ON_TOP);
        }
        else {
          PsiType newType = parameters[0].getType();

          Pair<PsiType, PsiElement> oldPair = typeMap.get(propertyName);
          if (oldPair == null) {
            typeMap.put(propertyName, new Pair<>(newType, method));
          }
          else {
            typeMap.put(propertyName, new Pair<>(newType, null));
          }
        }
      }
      else {
        PsiType closureType = null;

        if (methodName.startsWith("add")) {
          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length != 1) continue;

          PsiClass paramClass = PsiTypesUtil.getPsiClass(parameters[0].getType());
          if (paramClass == null || !InheritanceUtil.isInheritor(paramClass, "java.util.EventListener")) continue;

          for (PsiMethod psiMethod : paramClass.getMethods()) {
            if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;

            if (forCompletion) {
              result.put(psiMethod.getName(), NamedArgumentDescriptor.SIMPLE_ON_TOP);
            }
            else {
              if (closureType == null) {
                closureType = JavaPsiFacade.getElementFactory(manager.getProject()).createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, call.getResolveScope());
              }

              result.put(psiMethod.getName(), new TypeCondition(closureType, method));
            }
          }
        }
      }
    }

    if (!forCompletion) {
      for (Map.Entry<String, Pair<PsiType, PsiElement>> entry : typeMap.entrySet()) {
        result.put(entry.getKey(), new TypeCondition(entry.getValue().first, entry.getValue().second));
      }
    }
  }
}
