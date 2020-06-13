// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public final class ClassContextFilter {

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
      map = new ConcurrentHashMap<>();
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
