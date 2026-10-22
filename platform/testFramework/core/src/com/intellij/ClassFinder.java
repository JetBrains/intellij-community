// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ClassFinder {
  private final List<String> classNameList;
  private final Path classPathRoot;

  public ClassFinder(final Path classPathRoot, final String rootPackage) {
    this.classPathRoot = classPathRoot;
    String directoryOffset = rootPackage.replace(".", classPathRoot.getFileSystem().getSeparator());
    if (Files.isRegularFile(classPathRoot)) {
      classNameList = findAndStoreTestClassesFromJar(directoryOffset);
    }
    else {
      classNameList = findAndStoreTestClasses(directoryOffset);
    }
  }

  private static @Nullable String computeClassName(final @NotNull Path path, final @NotNull Path root) {
    Path absPath = path.toAbsolutePath();
    String fileName = absPath.getFileName().toString();
    if (fileName.endsWith(".class")) {
      return getClassFQN(absPath, root);
    }
    return null;
  }

  private static @NotNull String getClassFQN(final @NotNull Path path, final @NotNull Path root) {
    String fqn = StringUtil
      .trimEnd(root.relativize(path).toString(), ".class")
      .replace(path.getFileSystem().getSeparator(), ".");
    return fqn;
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
