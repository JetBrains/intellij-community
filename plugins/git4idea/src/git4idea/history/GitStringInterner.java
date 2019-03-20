/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cache of String objects that can be used to avoid creating duplicate strings with the same content.
 * This class is thread-safe but the effects of {@link #clearAndEnable} and {@link #clearAndDisable} methods
 * may not be visible to all threads immediately.
 */
public class GitStringInterner {
  @Nullable private static ConcurrentMap<String, String> ourStringCache;
  @NotNull private static final AtomicLong ourTotalSize = new AtomicLong();
  @NotNull private static final AtomicLong ourUniqueSize = new AtomicLong();

  /**
   * Enables caching of strings and clears the cache if it was not empty.
   */
  public static void clearAndEnable() {
    ourTotalSize.set(0);
    ourUniqueSize.set(0);
    ourStringCache = new ConcurrentHashMap<>();
  }

  /**
   * Disables caching of strings and clears the cache.
   *
   * @return the hit ratio for the time the cache was enabled defined as the total length of strings returned
   *     from the cache divided by the total length of strings passed to the {@link #intern(String)} method.
   */
  public static double clearAndDisable() {
    long totalSize = ourTotalSize.getAndSet(0);
    long uniqueSize = ourUniqueSize.getAndSet(0);
    ourStringCache = null;
    return totalSize == 0 ? 0 : (totalSize - uniqueSize) / (double)totalSize;
  }

  /**
   * Returns the string from the cache equal to the argument, if present, or puts the string to the cache and returns it.
   *
   * @param str the string to intern
   * @return the interned string
   */
  @NotNull
  public static String intern(@NotNull String str) {
    ConcurrentMap<String, String> cache = ourStringCache;
    if (cache == null) {
      return str;
    }
    String s = cache.computeIfAbsent(str, Function.identity());
    int length = s.length();
    ourTotalSize.addAndGet(length);
    if (s == str) {
      ourUniqueSize.addAndGet(length);
    }
    return s;
  }
}
