// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A collection of utility methods for working with PATH environment variable.
 */
@SuppressWarnings("IO_FILE_USAGE")
public final class PathEnvironmentVariableUtil {
  private static final Map<String, Boolean> ourOnPathCache = Collections.synchronizedMap(new HashMap<>());

  private PathEnvironmentVariableUtil() { }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return {@link File} instance or null if not found
   */
  public static @Nullable File findInPath(@NotNull String fileBaseName) {
    return findInPath(fileBaseName, null);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in PATH environment variable and is accepted by filter.
   *
   * @param fileBaseName file base name
   * @param filter       exe file filter
   * @return {@link File} instance or null if not found
   */
  public static @Nullable File findInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findInPath(fileBaseName, getPathVariableValue(), filter);
  }

  /**
   * Finds an executable file with the specified base name, that is located in a directory
   * listed in the given PATH environment variable value and is accepted by filter.
   *
   * @param fileBaseName      file base name
   * @param pathVariableValue value of PATH environment variable
   * @param filter            exe file filter
   * @return {@link File} instance or null if not found
   */
  public static @Nullable File findInPath(@NotNull String fileBaseName, @Nullable String pathVariableValue, @Nullable FileFilter filter) {
    var exeFiles = findExeFilesInPath(true, filter, pathVariableValue, fileBaseName);
    return !exeFiles.isEmpty() ? exeFiles.get(0) : null;
  }

  /**
   * Finds all executable files with the specified base name, that are located in directories
   * from PATH environment variable.
   *
   * @param fileBaseName file base name
   * @return file list
   */
  public static @NotNull List<File> findAllExeFilesInPath(@NotNull String fileBaseName) {
    return findAllExeFilesInPath(fileBaseName, null);
  }

  public static @NotNull List<File> findAllExeFilesInPath(@NotNull String fileBaseName, @Nullable FileFilter filter) {
    return findExeFilesInPath(false, filter, getPathVariableValue(), fileBaseName);
  }

  private static @NotNull List<File> findExeFilesInPath(
    boolean stopAfterFirstMatch,
    @Nullable FileFilter filter,
    @Nullable String pathEnvVarValue,
    String @NotNull ... fileBaseNames
  ) {
    if (pathEnvVarValue == null) {
      return List.of();
    }
    var result = new SmartList<File>();
    var dirPaths = getPathDirs(pathEnvVarValue);
    for (var dirPath : dirPaths) {
      var dir = new File(dirPath);
      if (dir.isAbsolute() && dir.isDirectory()) {
        for (var fileBaseName : fileBaseNames) {
          var exeFile = new File(dir, fileBaseName);
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
    return StringUtil.split(pathEnvVarValue, File.pathSeparator, true, true);
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
          return exeFiles.get(0).getAbsolutePath();
        }
      }
    }
    return defaultPath;
  }

  /**
   * Retrieves the value of PATH environment variable
   */
  public static @Nullable String getPathVariableValue() {
    return EnvironmentUtil.getValue("PATH");
  }

  public static @Nullable File findExecutableInPathOnAnyOS(@NotNull String baseName) {
    if (OS.CURRENT == OS.Windows) {
      var fileNames = ContainerUtil.map2Array(getWindowsExecutableFileExtensions(), String.class, ext -> baseName + ext);
      var exeFiles = findExeFilesInPath(true, null, getPathVariableValue(), fileNames);
      return !exeFiles.isEmpty() ? exeFiles.get(0) : null;
    }
    else {
      return findInPath(baseName);
    }
  }

  /**
   * Checks whether the given file is in one of the directories listed in the PATH environment variable.
   * The first call might be slow, but the result is cached.
   *
   * @since 2025.3
   */
  public static boolean isOnPath(@NotNull String name) {
    if (name.indexOf('\\') >= 0 || name.indexOf('/') >= 0) throw new IllegalArgumentException(name);
    var result = ourOnPathCache.get(name);
    if (result == null) {
      result = Boolean.FALSE;
      var path = getPathVariableValue();
      if (path != null) {
        for (var dir : StringUtil.tokenize(path, File.pathSeparator)) {
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
}
