// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// A collection of utility methods for working with the `PATH` environment variable.
@SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
public final class PathEnvironmentVariableUtil {
  private static final Map<String, Boolean> ourOnPathCache = Collections.synchronizedMap(new HashMap<>());

  private PathEnvironmentVariableUtil() { }

  /// Finds an executable file with the specified base name located in a directory listed in the `PATH` environment variable.
  ///
  /// @param fileBaseName file base name
  /// @return [java.io.File] instance, or `null` if not found
  public static @Nullable java.io.File findInPath(@NotNull String fileBaseName) {
    return findInPath(fileBaseName, null);
  }

  /// Finds an executable file with the specified base name located in a directory listed in the `PATH` environment variable
  /// and accepted by the filter.
  ///
  /// @param fileBaseName file base name
  /// @param filter       exe file filter
  /// @return [java.io.File] instance, or `null` if not found
  public static @Nullable java.io.File findInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findInPath(fileBaseName, getPathVariableValue(), filter);
  }

  /// Finds an executable file with the specified base name located in a directory listed in the given environment variable value
  /// and accepted by the filter.
  ///
  /// @param fileBaseName      file base name
  /// @param pathVariableValue value of a `PATH`-like environment variable
  /// @param filter            exe file filter
  /// @return [java.io.File] instance or null if not found
  public static @Nullable java.io.File findInPath(@NotNull String fileBaseName, @Nullable String pathVariableValue, @Nullable FileFilter filter) {
    var exeFiles = findExeFilesInPath(true, filter, pathVariableValue, fileBaseName);
    return !exeFiles.isEmpty() ? exeFiles.getFirst() : null;
  }

  /// Finds all executable files with the specified base name located in directories listed in the `PATH` environment variable.
  ///
  /// @param fileBaseName file base name
  /// @return file list
  public static @NotNull List<java.io.File> findAllExeFilesInPath(@NotNull String fileBaseName) {
    return findAllExeFilesInPath(fileBaseName, null);
  }

  public static @NotNull List<java.io.File> findAllExeFilesInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findExeFilesInPath(false, filter, getPathVariableValue(), fileBaseName);
  }

  private static @NotNull List<java.io.File> findExeFilesInPath(
    boolean stopAfterFirstMatch,
    @Nullable FileFilter filter,
    @Nullable String pathEnvVarValue,
    String @NotNull ... fileBaseNames
  ) {
    if (pathEnvVarValue == null) {
      return List.of();
    }
    var result = new SmartList<java.io.File>();
    var dirPaths = getPathDirs(pathEnvVarValue);
    for (var dirPath : dirPaths) {
      var dir = new java.io.File(dirPath);
      if (dir.isAbsolute() && dir.isDirectory()) {
        for (var fileBaseName : fileBaseNames) {
          var exeFile = new java.io.File(dir, fileBaseName);
          if (exeFile.isFile() && exeFile.canExecute()) {
            if (filter == null || filter.accept(exeFile)) {
              result.add(exeFile);
              if (stopAfterFirstMatch) {
                return result;
              }
            }
          }
        }
      }
    }
    return result;
  }

  public static @NotNull @Unmodifiable List<String> getPathDirs(@NotNull String pathEnvVarValue) {
    return StringUtil.split(pathEnvVarValue, java.io.File.pathSeparator, true, true);
  }

  public static @NotNull @Unmodifiable List<String> getWindowsExecutableFileExtensions() {
    if (OS.CURRENT == OS.Windows) {
      @SuppressWarnings("SpellCheckingInspection") var allExtensions = System.getenv("PATHEXT");
      if (allExtensions != null) {
        return StringUtil.split(allExtensions, ";", true, true).stream()
          .filter(ext -> ext.startsWith("."))
          .map(ext -> ext.toLowerCase(Locale.ROOT))
          .toList();
      }
    }
    return List.of();
  }

  public static @NotNull String findExecutableInWindowsPath(@NotNull String baseName) {
    return findExecutableInWindowsPath(baseName, baseName);
  }

  @Contract("_, !null -> !null")
  public static String findExecutableInWindowsPath(@NotNull String baseName, @Nullable String defaultPath) {
    if (OS.CURRENT == OS.Windows) {
      if (!StringUtil.containsChar(baseName, '/') && !StringUtil.containsChar(baseName, '\\')) {
        var fileNames = ContainerUtil.map2Array(getWindowsExecutableFileExtensions(), String.class, ext -> baseName + ext);
        var exeFiles = findExeFilesInPath(true, null, getPathVariableValue(), fileNames);
        if (!exeFiles.isEmpty()) {
          return exeFiles.getFirst().getAbsolutePath();
        }
      }
    }
    return defaultPath;
  }

  /// Retrieves the value of the `PATH` environment variable.
  public static @Nullable String getPathVariableValue() {
    return EnvironmentUtil.getValue("PATH");
  }

  public static @Nullable java.io.File findExecutableInPathOnAnyOS(@NotNull String baseName) {
    if (OS.CURRENT == OS.Windows) {
      var fileNames = ContainerUtil.map2Array(getWindowsExecutableFileExtensions(), String.class, ext -> baseName + ext);
      var exeFiles = findExeFilesInPath(true, null, getPathVariableValue(), fileNames);
      return !exeFiles.isEmpty() ? exeFiles.getFirst() : null;
    }
    else {
      return findInPath(baseName);
    }
  }

  /// Checks whether the given file is in one of the directories listed in the PATH environment variable.
  /// The first call might be slow, but the result is cached.
  ///
  /// @since 2025.3
  public static boolean isOnPath(@NotNull String name) {
    if (name.indexOf('\\') >= 0 || name.indexOf('/') >= 0) throw new IllegalArgumentException(name);
    var result = ourOnPathCache.get(name);
    if (result == null) {
      result = Boolean.FALSE;
      var path = getPathVariableValue();
      if (path != null) {
        for (var dir : StringUtil.tokenize(path, java.io.File.pathSeparator)) {
          if (Files.isExecutable(Path.of(dir, name))) {
            result = Boolean.TRUE;
            break;
          }
        }
      }
      ourOnPathCache.put(name, result);
    }
    return result;
  }

  @ApiStatus.Internal
  public static void appendSearchPath(@NotNull Map<String, String> env, @NotNull String envName, @NotNull String pathToAppend) {
    var currentPath = env.get(envName);
    var newPath = currentPath != null ? currentPath + java.io.File.pathSeparator + pathToAppend : pathToAppend;
    env.put(envName, newPath);
  }
}
