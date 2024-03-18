// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ClassFinder {
  private static final List<String> IGNORED_CLASS_NAMES = Arrays.asList("com.intellij.tests.BootstrapTests", "com.intellij.AllTests");
  private final List<String> classNameList;
  private final Path classPathRoot;
  private final boolean includeUnconventionallyNamedTests;

  public ClassFinder(final Path classPathRoot, final String rootPackage, boolean includeUnconventionallyNamedTests) {
    this.classPathRoot = classPathRoot;
    this.includeUnconventionallyNamedTests = includeUnconventionallyNamedTests;
    String directoryOffset = rootPackage.replace(".", classPathRoot.getFileSystem().getSeparator());
    classNameList = findAndStoreTestClasses(classPathRoot.resolve(directoryOffset));
  }

  /**
   * @deprecated Use {@linkplain ClassFinder#ClassFinder(Path, String, boolean)} instead.
   */
  @Deprecated(forRemoval = true)
  public ClassFinder(final File classPathRoot, final String rootPackage, boolean includeUnconventionallyNamedTests) {
    this(classPathRoot.toPath(), rootPackage, includeUnconventionallyNamedTests);
  }

  @Nullable
  private String computeClassName(final @NotNull Path path) {
    Path absPath = path.toAbsolutePath();
    String fileName = absPath.getFileName().toString();
    if (!includeUnconventionallyNamedTests) {
      // It's faster and easier to check for `endsWith` rather than `extensionEquals` (see below) which does `endsWith`
      if (fileName.endsWith("Test.class")) {
        return getClassFQN(absPath);
      }
      return null;
    }
    if (!FileUtilRt.extensionEquals(fileName, "class")) {
      return null;
    }
    if (isSuitableTestClassName(FileUtilRt.getNameWithoutExtension(fileName), includeUnconventionallyNamedTests)) {
      return getClassFQN(absPath);
    }
    return null;
  }

  static boolean isSuitableTestClassName(@NotNull String className, boolean includeUnconventionallyNamedTests) {
    if (!includeUnconventionallyNamedTests) {
      return className.endsWith("Test");
    }
    if (!className.contains("Test") && !className.contains("Suite")) return false;
    className = StringsKt.substringAfterLast(className, '.', className);
    List<String> names = Arrays.asList(className.split("\\$"));
    Collections.reverse(names);
    for (String name : names) {
      // most likely something like RecursionManagerTest$_testMayCache_closure5 or other anonymous class
      // may cause https://issues.apache.org/jira/browse/GROOVY-5351
      if (!Character.isUpperCase(name.charAt(0))) return false;

      // A test may be named Test*, *Test, *Tests*, *TestCase, *TestSuite, *Suite, etc
      List<String> words = Arrays.asList(NameUtilCore.nameToWords(name));

      if (words.contains("Test") || words.contains("Tests") || words.contains("Suite")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private String getClassFQN(final @NotNull Path path) {
    String fqn = StringUtil
      .trimEnd(classPathRoot.relativize(path).toString(), ".class")
      .replace(path.getFileSystem().getSeparator(), ".");
    return IGNORED_CLASS_NAMES.contains(fqn) ? null : fqn;
  }

  private List<String> findAndStoreTestClasses(@NotNull Path current) {
    if (!Files.exists(current)) {
      return Collections.emptyList();
    }
    try (Stream<Path> walk = Files.walk(current)) {
      return walk
        .filter(path -> Files.isRegularFile(path))
        .map(path -> computeClassName(path))
        .filter(name -> name != null)
        .toList();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<String> getClasses() {
    return classNameList;
  }
}
