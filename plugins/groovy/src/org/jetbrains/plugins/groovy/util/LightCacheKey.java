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

    if (userData == null || holder.getManager().getModificationTracker().getModificationCount() != userData.first) {
      return null;
    }

    return userData.second;
  }

  public T putCachedValue(PsiElement holder, @NotNull T value) {
    long modificationCount = holder.getManager().getModificationTracker().getModificationCount();

    Pair<Long, T> pair = Pair.create(modificationCount, value);

    Pair<Long, T> oldValue = ((UserDataHolderEx)holder).putUserDataIfAbsent(key, pair);
    if (oldValue == pair) {
      return value;
    }

    if (oldValue.first == modificationCount) {
      return oldValue.second;
    }

    if (((UserDataHolderEx)holder).replace(key, oldValue, pair)) {
      return value;
    }

    Pair<Long, T> createdFromOtherThreadValue = holder.getUserData(key);
    assert createdFromOtherThreadValue.first == modificationCount;

    return createdFromOtherThreadValue.second;
  }

  public static <T> LightCacheKey<T> create() {
    return new LightCacheKey<T>();
  }

}
