/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class NonCodeMembersHolder implements CustomMembersHolder {
  private final List<LightMethodBuilder> myMethods = new ArrayList<LightMethodBuilder>();
  private static final Key<CachedValue<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>>> CACHED_HOLDERS = Key.create("CACHED_HOLDERS");

  public static NonCodeMembersHolder generateMembers(Set<Map> methods, final PsiFile place) {
    return CachedValuesManager.getManager(place.getProject()).getCachedValue(place, CACHED_HOLDERS, new CachedValueProvider<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>>() {
      public Result<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>> compute() {
        final ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder> map = new ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>() {
          @Override
          protected NonCodeMembersHolder create(Set<Map> key) {
            return new NonCodeMembersHolder(key, place);
          }
        };
        return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      }
    }, false).get(methods);
  }

  public NonCodeMembersHolder(Set<Map> data, PsiElement place) {
    final PsiManager manager = place.getManager();
    for (Map prop : data) {
      final PsiType type = convertToPsiType((String)prop.get("type"), place);
      final String name = (String)prop.get("name");
      final LightMethodBuilder method = new LightMethodBuilder(manager, name).
        addModifier(PsiModifier.PUBLIC).
        setReturnType(type);
      final Object params = prop.get("params");
      if (params instanceof Map) {
        for (Object paramName : ((Map)params).keySet()) {
          method.addParameter(String.valueOf(paramName), convertToPsiType((String)((Map)params).get(paramName), place));
        }
      }
      if (Boolean.TRUE.equals(prop.get("isStatic"))) {
        method.addModifier(PsiModifier.STATIC);
      }

      final Object bindsTo = prop.get("bindsTo");
      if (bindsTo instanceof PsiElement) {
        method.setNavigationElement((PsiElement)bindsTo);
      }

      myMethods.add(method);
    }
  }

  private static PsiType convertToPsiType(String type, PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }

  public boolean processMembers(PsiScopeProcessor processor) {
    for (PsiMethod method : myMethods) {
      if (!ResolveUtil.processElement(processor, method)) {
        return false;
      }
    }
    return true;
  }
}
