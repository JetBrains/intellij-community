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

package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.*;

/**
 * @author ilyas
 */
public class DuplicatesUtil {
  public static void collectMethodDuplicates(Map<GrMethod, List<GrMethod>> map, HashSet<GrMethod> duplicateMethodsWarning, HashSet<GrMethod> duplicateMethodsErrors) {
    for (GrMethod method : map.keySet()) {
      List<GrMethod> duplicateMethods = map.get(method);

      if (duplicateMethods != null && duplicateMethods.size() > 1) {
        HashMap<PsiType, GrMethod> duplicateMethodsToReturnTypeMap = new HashMap<>();

        for (GrMethod duplicateMethod : duplicateMethods) {
          GrTypeElement typeElement = duplicateMethod.getReturnTypeElementGroovy();

          PsiType methodReturnType;
          if (typeElement != null) {
            methodReturnType = typeElement.getType();
          } else {
            methodReturnType = PsiType.NULL;
          }

          duplicateMethodsWarning.add(duplicateMethod);

          GrMethod grMethodWithType = duplicateMethodsToReturnTypeMap.get(methodReturnType);
          if (grMethodWithType != null) {
            duplicateMethodsErrors.add(duplicateMethod);
            duplicateMethodsErrors.add(grMethodWithType);
            duplicateMethodsWarning.remove(duplicateMethod);
            duplicateMethodsWarning.remove(grMethodWithType);
          }

          duplicateMethodsToReturnTypeMap.put(methodReturnType, duplicateMethod);
        }
      }
    }
  }

  public static <D extends PsiElement> Map<D, List<D>> factorDuplicates(D[] elements, TObjectHashingStrategy<D> strategy) {
    if (elements == null || elements.length == 0) return Collections.emptyMap();

    THashMap<D, List<D>> map = new THashMap<>(strategy);

    for (D element : elements) {
      List<D> list = map.get(element);
      if (list == null) {
        list = new ArrayList<>();
      }
      list.add(element);
      map.put(element, list);
    }

    return map;
  }
}
