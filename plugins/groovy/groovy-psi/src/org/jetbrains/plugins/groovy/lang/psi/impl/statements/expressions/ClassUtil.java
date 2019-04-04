// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ClassUtil {
  private static final LightCacheKey<Map<String, PsiClass>> PARENT_CACHE_KEY = LightCacheKey.create();

  public static Map<String, PsiClass> getSuperClassesWithCache(@NotNull PsiClass aClass) {
    Map<String, PsiClass> superClassNames = PARENT_CACHE_KEY.getCachedValue(aClass);
    if (superClassNames == null) {
      Set<PsiClass> superClasses = new THashSet<>();
      superClasses.add(aClass);
      InheritanceUtil.getSuperClasses(aClass, superClasses, true);

      superClassNames = new LinkedHashMap<>();
      for (PsiClass superClass : superClasses) {
        superClassNames.put(superClass.getQualifiedName(), superClass);
      }

      superClassNames = PARENT_CACHE_KEY.putCachedValue(aClass, superClassNames);
    }

    return superClassNames;
  }
}
