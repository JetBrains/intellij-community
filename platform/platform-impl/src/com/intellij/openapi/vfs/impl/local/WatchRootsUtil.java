// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

class WatchRootsUtil {
  public static final Comparator<String> FILE_NAME_COMPARATOR = SystemInfo.isFileSystemCaseSensitive
                                                                ? String::compareTo
                                                                : String::compareToIgnoreCase;

  public static boolean isCoveredRecursively(@NotNull NavigableSet<String> recursiveRoots, @NotNull String path) {
    String recursiveRoot = recursiveRoots.floor(path);
    return recursiveRoot != null && FileUtil.startsWith(path, recursiveRoot);
  }

  public static <T> void collectByPrefix(@NotNull NavigableMap<String, T> paths,
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

  public static void insertRecursivePath(@NotNull NavigableSet<String> recursiveRoots, @NotNull String path) {
    if (!isCoveredRecursively(recursiveRoots, path)) {
      recursiveRoots.add(path);
      // Remove any roots covered by newly added
      String higher;
      while ((higher = recursiveRoots.higher(path)) != null && FileUtil.startsWith(higher, path)) {
        recursiveRoots.remove(higher);
      }
    }
  }

  public static void forEachFilePathSegment(@SystemIndependent @NotNull String filePath,
                                            char separator, @NotNull Processor<? super String> consumer) {
    int position = filePath.indexOf(separator);
    int length = filePath.length();
    while (position >= 0 && position < length) {
      String subPath = filePath.substring(0, position + 1);
      if (!consumer.process(subPath)) {
        break;
      }
      position = filePath.indexOf(separator, position + 1);
    }
  }

  @NotNull
  public static NavigableSet<String> optimizeFlatRoots(@NotNull Iterable<String> flatRoots,
                                                       @NotNull NavigableSet<String> recursiveRoots) {
    NavigableSet<String> result = createFileNavigableSet();
    for (String flatRoot : flatRoots) {
      if (!isCoveredRecursively(recursiveRoots, flatRoot)) {
        result.add(flatRoot);
      }
    }
    return result;
  }

  @NotNull
  public static NavigableSet<String> createFileNavigableSet() {
    return new TreeSet<>(FILE_NAME_COMPARATOR);
  }

  @NotNull
  public static <T> NavigableMap<String, T> createFileNavigableMap() {
    return new TreeMap<>(FILE_NAME_COMPARATOR);
  }

  @Contract("null -> null; !null -> !null")
  @SystemIndependent
  public static String normalizeFileName(String path) {
    if (path != null) {
      path = FileUtil.toSystemIndependentName(path);
      if (!path.endsWith("/")) {
        path += "/";
      }
      return path;
    }
    return null;
  }

  public static boolean removeRecursivePath(@NotNull NavigableSet<String> optimizedRecursiveRoots,
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

  @Nullable
  @SystemIndependent
  static String mapToSystemPath(@SystemIndependent @NotNull String rootPath) {
    int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (index >= 0) rootPath = rootPath.substring(0, index);

    File rootFile = new File(FileUtil.toSystemDependentName(rootPath));
    if (!rootFile.isAbsolute()) {
      WatchRootsManager.LOG.warn("Invalid path: " + rootPath);
      return null;
    }
    return normalizeFileName(rootFile.getAbsolutePath());
  }
}
