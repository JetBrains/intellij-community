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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import java.util.Map;

/**
 * @author peter
 */
public class FactorTree extends UserDataHolderBase {
  private static final Key<CachedValue<Map>> GDSL_MEMBER_CACHE = Key.create("GDSL_MEMBER_CACHE");
  private static final Key<Boolean> CONTAINS_TYPE = Key.create("CONTAINS_TYPE");
  private final CachedValueProvider<Map> myProvider;
  private final CachedValue<Map> myTopLevelCache;
  private final GroovyDslExecutor myExecutor;

  public FactorTree(final Project project, GroovyDslExecutor executor) {
    myExecutor = executor;
    myProvider = () -> new CachedValueProvider.Result<>(ContainerUtil.newConcurrentMap(), PsiModificationTracker.MODIFICATION_COUNT);
    myTopLevelCache = CachedValuesManager.getManager(project).createCachedValue(myProvider, false);
  }

  public void cache(GroovyClassDescriptor descriptor, CustomMembersHolder holder) {
    Map current = null;
    for (Factor factor : descriptor.affectingFactors) {
      Object key;
      switch (factor) {
        case placeElement: key = descriptor.getPlace(); break;
        case placeFile: key = descriptor.getPlaceFile(); break;
        case qualifierType: key = descriptor.getPsiType().getCanonicalText(false); break;
        default: throw new IllegalStateException("Unknown variant: "+ factor);
      }
      if (current == null) {
        if (key instanceof UserDataHolder) {
          final Project project = descriptor.getProject();
          current = CachedValuesManager.getManager(project).getCachedValue((UserDataHolder)key, GDSL_MEMBER_CACHE, myProvider, false);
          continue;
        }

        current = myTopLevelCache.getValue();
      }
      Map next = (Map)current.get(key);
      if (next == null) {
        //noinspection unchecked
        current.put(key, next = ContainerUtil.newConcurrentMap());
        if (key instanceof String) { // type
          //noinspection unchecked
          current.put(CONTAINS_TYPE, true);
        }
      }
      current = next;
    }

    if (current == null) current = myTopLevelCache.getValue();
    //noinspection unchecked
    current.put(myExecutor, holder);
  }

  @Nullable
  public CustomMembersHolder retrieve(PsiElement place, PsiFile placeFile, NotNullLazyValue<String> qualifierType) {
    return retrieveImpl(place, placeFile, qualifierType, myTopLevelCache.getValue(), true);
  }

  @Nullable
  private CustomMembersHolder retrieveImpl(@NotNull PsiElement place, @NotNull PsiFile placeFile, @NotNull NotNullLazyValue<String> qualifierType, @Nullable Map current, boolean topLevel) {
    if (current == null) return null;

    CustomMembersHolder result;

    result = (CustomMembersHolder)current.get(myExecutor);
    if (result != null) return result;

    if (current.containsKey(CONTAINS_TYPE)) {
      result = retrieveImpl(place, placeFile, qualifierType, (Map)current.get(qualifierType.getValue()), false);
      if (result != null) return result;
    }

    result = retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(placeFile, current, topLevel), false);
    if (result != null) return result;

    return retrieveImpl(place, placeFile, qualifierType, getFromMapOrUserData(place, current, topLevel), false);
  }

  private static Map getFromMapOrUserData(UserDataHolder holder, Map map, boolean fromUserData) {
    if (fromUserData) {
      CachedValue<Map> cache = holder.getUserData(GDSL_MEMBER_CACHE);
      return cache != null && cache.hasUpToDateValue() ? cache.getValue() : null;
    }
    return (Map)map.get(holder);
  }
}

