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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ClassUtil {
  private static final LightCacheKey<Map<String, PsiClass>> PARENT_CACHE_KEY = LightCacheKey.create();

  public static Map<String, PsiClass> getSuperClassesWithCache(@NotNull PsiClass aClass) {
    Map<String, PsiClass> superClassNames = PARENT_CACHE_KEY.getCachedValue(aClass);
    if (superClassNames == null) {
      Set<PsiClass> superClasses = new THashSet<PsiClass>();
      superClasses.add(aClass);
      InheritanceUtil.getSuperClasses(aClass, superClasses, true);

      superClassNames = new LinkedHashMap<String, PsiClass>();
      for (PsiClass superClass : superClasses) {
        superClassNames.put(superClass.getQualifiedName(), superClass);
      }

      superClassNames = PARENT_CACHE_KEY.putCachedValue(aClass, superClassNames);
    }

    return superClassNames;
  }

  @NotNull
  public static PsiType findPsiType(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    String typeText = descriptor.getTypeText();
    final String key = getClassKey(typeText);
    final Object cached = ctx.get(key);
    if (cached instanceof PsiType) {
      return (PsiType)cached;
    }

    final PsiType found = JavaPsiFacade.getElementFactory(descriptor.getProject()).createTypeFromText(typeText, descriptor.getPlaceFile());
    ctx.put(key, found);
    return found;
  }

  public static String getClassKey(String fqName) {
    return "Class: " + fqName;
  }
}
