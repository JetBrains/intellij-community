// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.*;

/**
 * @author ilyas
 */
public final class DuplicatesUtil {
  public static void collectMethodDuplicates(Map<GrMethod, List<GrMethod>> map, HashSet<? super GrMethod> duplicateMethodsWarning, HashSet<? super GrMethod> duplicateMethodsErrors) {
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

  public static <D extends PsiElement> Map<D, List<D>> factorDuplicates(D[] elements, Hash.Strategy<D> strategy) {
    if (elements == null || elements.length == 0) {
      return Collections.emptyMap();
    }

    Map<D, List<D>> map = new Object2ObjectOpenCustomHashMap<>(strategy);
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
