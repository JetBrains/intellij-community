// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Unless stated otherwise, methods in the class are system-agnostic - i.e. capable to work with both OS and VFS paths.
 * Care should be taken though to make sure that within each call of a method parameters are of the same breed.
 */
final class WatchRootsUtil {
  static boolean isCoveredRecursively(@NotNull NavigableSet<String> recursiveRoots, @NotNull String path) {
    String recursiveRoot = recursiveRoots.floor(path);
    return recursiveRoot != null && FileUtil.startsWith(path, recursiveRoot);
  }

  static <T> void collectByPrefix(@NotNull NavigableMap<String, T> paths,
                                  @NotNull String prefix,
                                  @NotNull Consumer<Map.Entry<String, T>> collector) {
    for (Map.Entry<String, T> entry : paths.tailMap(prefix, false).entrySet()) {
      if (FileUtil.startsWith(entry.getKey(), prefix)) {
        collector.accept(entry);
      }
      else {
        break;
      }
    }
  }

  static void insertRecursivePath(@NotNull NavigableSet<String> recursiveRoots, @NotNull String path) {
    if (!isCoveredRecursively(recursiveRoots, path)) {
      recursiveRoots.add(path);
      // Remove any roots covered by newly added
      String higher;
      while ((higher = recursiveRoots.higher(path)) != null && FileUtil.startsWith(higher, path)) {
        recursiveRoots.remove(higher);
      }
    }
  }

  static void forEachPathSegment(@NotNull String path, char separator, @NotNull Predicate<String> consumer) {
    int position = path.indexOf(separator);
    int length = path.length();
    while (position >= 0 && position < length) {
      String subPath = path.substring(0, position);
      if (!consumer.test(subPath)) {
        return;
      }
      position = path.indexOf(separator, position + 1);
    }
    consumer.test(path);
  }

  static @NotNull NavigableSet<String> optimizeFlatRoots(@NotNull Iterable<String> flatRoots, @NotNull NavigableSet<String> recursiveRoots) {
    NavigableSet<String> result = createFileNavigableSet();
    for (String flatRoot : flatRoots) {
      if (!isCoveredRecursively(recursiveRoots, flatRoot)) {
        result.add(flatRoot);
      }
    }
    return result;
  }

  static @NotNull NavigableSet<String> createFileNavigableSet() {
    return new TreeSet<>(OSAgnosticPathUtil.COMPARATOR);
  }

  static <T> @NotNull NavigableMap<String, T> createFileNavigableMap() {
    return new TreeMap<>(OSAgnosticPathUtil.COMPARATOR);
  }

  static boolean removeRecursivePath(@NotNull NavigableSet<String> optimizedRecursiveRoots,
                                     @NotNull NavigableMap<String, ?> sourceRecursiveRoots,
                                     @NotNull String path) {
    if (!optimizedRecursiveRoots.remove(path)) {
      return false;
    }
    Ref<String> lastPathRef = new Ref<>();
    collectByPrefix(sourceRecursiveRoots, path, entry -> {
      String lastPath = lastPathRef.get();
      String childPath = entry.getKey();
      if (lastPath == null || !FileUtil.startsWith(childPath, lastPath)) {
        optimizedRecursiveRoots.add(childPath);
        lastPathRef.set(childPath);
      }
    });
    return true;
  }
}