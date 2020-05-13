// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    myProvider = () -> new CachedValueProvider.Result<>((ConcurrentMap<Object, Object>)new ConcurrentHashMap<Object, Object>(), PsiModificationTracker.MODIFICATION_COUNT);
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
        current.put(key, next = new ConcurrentHashMap<>());
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

  public @Nullable CustomMembersHolder retrieve(PsiElement place, PsiFile placeFile, NotNullLazyValue<String> qualifierType) {
    return retrieveImpl(place, placeFile, qualifierType, myTopLevelCache.getValue(), true);
  }

  private @Nullable CustomMembersHolder retrieveImpl(@NotNull PsiElement place, @NotNull PsiFile placeFile, @NotNull NotNullLazyValue<String> qualifierType, @Nullable Map current, boolean topLevel) {
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

