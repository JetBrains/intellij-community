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
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Map;

/**
 * @author peter
 */
public class ClassContextFilter {

  public static ContextFilter fromClassPattern(ElementPattern pattern) {
    return (descriptor, ctx) -> {
      PsiType type = descriptor.getPsiType();
      return type instanceof PsiClassType && pattern.accepts(((PsiClassType)type).resolve());
    };
  }

  public static ContextFilter subtypeOf(String typeText) {
    return (descriptor, ctx) -> isSubtype(descriptor.getPsiType(), descriptor.justGetPlaceFile(), typeText);
  }

  public static boolean isSubtype(PsiType checked, PsiFile placeFile, String typeText) {
    final boolean isClassType = checked instanceof PsiClassType;
    if (isClassType) {
      final PsiClass psiClass = ((PsiClassType)checked).resolve();
      if (psiClass != null) {
        final int i = typeText.indexOf("<");
        String rawName = i > 0 ? typeText.substring(0, i) : typeText;
        if (!ClassUtil.getSuperClassesWithCache(psiClass).containsKey(rawName)) {
          return false;
        }
      }
    }

    PsiType myType = getCachedType(typeText, placeFile);
    if (checked == PsiType.NULL) return myType == PsiType.NULL;
    return TypesUtil.isAssignableByMethodCallConversion(myType, checked, placeFile);
  }

  private static final Key<Map<String, PsiType>> CACHED_TYPES = Key.create("Cached types");

  public static PsiType getCachedType(String typeText, PsiFile context) {
    Map<String, PsiType> map = context.getUserData(CACHED_TYPES);
    if (map == null) {
      map = ContainerUtil.newConcurrentMap();
      context.putUserData(CACHED_TYPES, map);
    }
    PsiType type = map.get(typeText);
    if (type == null || !type.isValid()) {
      type = JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(typeText, context);
      map.put(typeText, type);
    }
    return type;
  }
}
