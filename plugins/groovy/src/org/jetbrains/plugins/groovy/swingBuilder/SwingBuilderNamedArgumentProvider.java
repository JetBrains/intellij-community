/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.swingBuilder;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SwingBuilderNamedArgumentProvider extends GroovyNamedArgumentProvider {

  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, NamedArgumentDescriptor> result) {
    PsiType returnType = ((PsiMethod)resolve).getReturnType();
    PsiClass aClass = PsiTypesUtil.getPsiClass(returnType);
    if (aClass == null) return;

    Map<String, Pair<PsiType, PsiElement>> typeMap = null;
    if (!forCompletion) {
      typeMap = new HashMap<String, Pair<PsiType, PsiElement>>();
    }

    PsiManager manager = aClass.getManager();

    for (PsiMethod method : aClass.getAllMethods()) {
      String propertyName = GroovyPropertyUtils.getPropertyNameBySetterName(method.getName());
      if (propertyName == null) continue;
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
          typeMap.put(propertyName, new Pair<PsiType, PsiElement>(newType, method));
        }
        else {
          PsiType type = TypesUtil.getLeastUpperBound(oldPair.first, newType, manager);
          if (type == null) {
            type = PsiType.getJavaLangObject(manager, aClass.getResolveScope());
          }
          typeMap.put(propertyName, new Pair<PsiType, PsiElement>(newType, null));
        }
      }
    }

    if (!forCompletion) {
      for (Map.Entry<String, Pair<PsiType, PsiElement>> entry : typeMap.entrySet()) {
        result.put(entry.getKey(), new NamedArgumentDescriptor.TypeCondition(entry.getValue().first, entry.getValue().second));
      }
    }
  }
}
