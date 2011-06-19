package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
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

  /**
   *
   * @param holder
   * @param defaultValue Value which will put to cache if cache is empty or outdated. Other threads will get this value.
   * @return
   */
  public T getCachedValue(PsiElement holder, @NotNull T defaultValue) {
    Pair<Long, T> userData = holder.getUserData(key);

    long modificationCount = holder.getManager().getModificationTracker().getModificationCount();
    if (userData != null && modificationCount == userData.first) {
      return userData.second;
    }

    Pair<Long, T> newUserData = Pair.create(modificationCount, defaultValue);



    return null;
  }

  public T putCachedValue(PsiElement holder, @Nullable T value) {
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
