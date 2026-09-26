// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ClassUtil {

  public static Map<String, PsiClass> getSuperClassesWithCache(@NotNull PsiClass aClass) {
    return CachedValuesManager.getCachedValue(aClass, () -> Result.create(
      doGetSuperClassesWithCache(aClass), PsiModificationTracker.MODIFICATION_COUNT
    ));
  }

  private static Map<String, PsiClass> doGetSuperClassesWithCache(@NotNull PsiClass aClass) {
    Set<PsiClass> superClasses = new LinkedHashSet<>();
    superClasses.add(aClass);
    InheritanceUtil.getSuperClasses(aClass, superClasses, true);

    Map<String, PsiClass> superClassNames = new LinkedHashMap<>();
    for (PsiClass superClass : superClasses) {
      superClassNames.put(superClass.getQualifiedName(), superClass);
    }
    return superClassNames;
  }
}
