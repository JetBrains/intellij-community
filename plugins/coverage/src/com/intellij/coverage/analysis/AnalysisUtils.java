// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@ApiStatus.Internal
public final class AnalysisUtils {
  private static final char ARCHIVE_ENTRY_SEPARATOR = '!';
  private static final String ARCHIVE_ENTRY_SEPARATOR_TEXT = "!/";

  static boolean isClassFile(@NotNull Path classFile) {
    return classFile.toString().endsWith(".class");
  }

  public static String getClassName(Path classFile) {
    return StringUtil.trimEnd(classFile.getFileName().toString(), ".class");
  }

  public static String getSourceToplevelFQName(String classFQVMName) {
    final int index = classFQVMName.indexOf('$');
    if (index > 0) classFQVMName = classFQVMName.substring(0, index);
    classFQVMName = StringUtil.trimStart(classFQVMName, "/");
    return internalNameToFqn(classFQVMName);
  }

  public static @NotNull String internalNameToFqn(@NotNull String internalName) {
    return internalName.replace('\\', '.').replace('/', '.');
  }

  public static @NotNull String fqnToInternalName(@NotNull String fqn) {
    return fqn.replace('.', '/');
  }

  public static @NotNull String buildVMName(@NotNull String packageVMName, @NotNull String simpleName) {
    return packageVMName.isEmpty() ? simpleName : packageVMName + "/" + simpleName;
  }

  static @NotNull Path toArchiveEntryPath(@NotNull Path archivePath, @NotNull String entryPath) {
    return Path.of(archivePath + ARCHIVE_ENTRY_SEPARATOR_TEXT + entryPath);
  }

  public static @Nullable ArchiveEntryPath splitArchiveEntryPath(@NotNull Path classFile) {
    return splitArchiveEntryPath(classFile.toString());
  }

  private static @Nullable ArchiveEntryPath splitArchiveEntryPath(@NotNull String classFilePath) {
    int separator = findArchiveEntrySeparator(classFilePath);
    if (separator < 0) return null;
    String archivePath = classFilePath.substring(0, separator);
    String entryPath = classFilePath.substring(separator + ARCHIVE_ENTRY_SEPARATOR_TEXT.length()).replace('\\', '/');
    return new ArchiveEntryPath(archivePath, entryPath);
  }

  private static int findArchiveEntrySeparator(@NotNull String classFilePath) {
    int separator = classFilePath.length();
    while (true) {
      separator = classFilePath.lastIndexOf(ARCHIVE_ENTRY_SEPARATOR, separator - 1);
      if (separator <= 0) return -1;
      if (separator < classFilePath.length() - ARCHIVE_ENTRY_SEPARATOR_TEXT.length()) {
        char entryPathSeparator = classFilePath.charAt(separator + 1);
        if (entryPathSeparator == '/' || entryPathSeparator == '\\') return separator;
      }
    }
  }

  public record ArchiveEntryPath(@NotNull String archivePath, @NotNull String entryPath) {
  }
}
