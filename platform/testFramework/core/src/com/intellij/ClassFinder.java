// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
    if (Files.isRegularFile(classPathRoot)) {
      classNameList = findAndStoreTestClassesFromJar(directoryOffset);
    }
    else {
      classNameList = findAndStoreTestClasses(directoryOffset);
    }
  }

  @Nullable
  private String computeClassName(final @NotNull Path path, final @NotNull Path root) {
    Path absPath = path.toAbsolutePath();
    String fileName = absPath.getFileName().toString();
    if (!includeUnconventionallyNamedTests) {
      // It's faster and easier to check for `endsWith` rather than `extensionEquals` (see below) which does `endsWith`
      if (fileName.endsWith("Test.class")) {
        return getClassFQN(absPath, root);
      }
      return null;
    }
    if (!FileUtilRt.extensionEquals(fileName, "class")) {
      return null;
    }
    if (isSuitableTestClassName(FileUtilRt.getNameWithoutExtension(fileName), includeUnconventionallyNamedTests)) {
      return getClassFQN(absPath, root);
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
  private static String getClassFQN(final @NotNull Path path, final @NotNull Path root) {
    String fqn = StringUtil
      .trimEnd(root.relativize(path).toString(), ".class")
      .replace(path.getFileSystem().getSeparator(), ".");
    return IGNORED_CLASS_NAMES.contains(fqn) ? null : fqn;
  }

  private List<String> findAndStoreTestClasses(@NotNull String directoryOffset) {
    final Path current = classPathRoot.resolve(directoryOffset);
    if (!Files.exists(current)) {
      return Collections.emptyList();
    }
    try (Stream<Path> walk = Files.walk(current)) {
      return walk
        .filter(path -> Files.isRegularFile(path))
        .map(path -> computeClassName(path, classPathRoot))
        .filter(name -> name != null)
        .toList();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<String> findAndStoreTestClassesFromJar(@NotNull String directoryOffset) {
    if (!Files.exists(classPathRoot)) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>();
    try (FileSystem fs = FileSystems.newFileSystem(classPathRoot)) {
      for (Path root : fs.getRootDirectories()) {
        Path offset = root.resolve(directoryOffset);
        if (!Files.exists(offset)) {
          continue;
        }
        try (Stream<Path> walk = Files.walk(offset)) {
          result.addAll(walk
                          .filter(path -> Files.isRegularFile(path))
                          .map(it -> computeClassName(it, root))
                          .filter(name -> name != null)
                          .toList());
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  public Collection<String> getClasses() {
    return classNameList;
  }
}
