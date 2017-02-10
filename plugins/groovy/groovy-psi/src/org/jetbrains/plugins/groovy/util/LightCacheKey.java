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
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class LightCacheKey<T> {

  private final Key<Pair<Long, T>> key = Key.create(this.toString());

  /**
   * @return Cached value or null if cached value is not exists or outdated.
   */
  @Nullable
  public T getCachedValue(PsiElement holder) {
    Pair<Long, T> userData = holder.getUserData(key);

    if (userData == null || getModificationCount(holder) != userData.first) {
      return null;
    }

    return userData.second;
  }

  protected long getModificationCount(PsiElement holder) {
    return holder.getManager().getModificationTracker().getModificationCount();
  }

  public T putCachedValue(PsiElement holder, @NotNull T value) {
    long modificationCount = getModificationCount(holder);

    Pair<Long, T> pair = Pair.create(modificationCount, value);

    Pair<Long, T> puttedValue = ((UserDataHolderEx)holder).putUserDataIfAbsent(key, pair);
    if (puttedValue == pair) {
      return value;
    }

    if (puttedValue.first == modificationCount) {
      return puttedValue.second;
    }

    if (((UserDataHolderEx)holder).replace(key, puttedValue, pair)) {
      return value;
    }

    Pair<Long, T> createdFromOtherThreadValue = holder.getUserData(key);
    //noinspection ConstantConditions
    assert createdFromOtherThreadValue.first == modificationCount;

    return createdFromOtherThreadValue.second;
  }

  public static <T> LightCacheKey<T> create() {
    return new LightCacheKey<>();
  }

  public static <T> LightCacheKey<T> createByJavaModificationCount() {
    return new LightCacheKey<T>() {
      @Override
      protected long getModificationCount(PsiElement holder) {
        return holder.getManager().getModificationTracker().getJavaStructureModificationCount();
      }
    };
  }

  public static <T> LightCacheKey<T> createByFileModificationCount() {
    return new LightCacheKey<T>() {
      @Override
      protected long getModificationCount(PsiElement holder) {
        return holder.getContainingFile().getModificationStamp();
      }
    };
  }

}
