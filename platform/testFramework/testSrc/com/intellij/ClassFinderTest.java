// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.rules.TempDirectoryExtension;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.BDDAssertions.then;

class ClassFinderTest {
  @RegisterExtension
  public TempDirectoryExtension tempDir = new TempDirectoryExtension();

  @Test
  void testFindClassesInJar() {
    Path jar = getJarWithClass(Test.class);
    
    checkJUnitClasses(jar);
  }

  @Test
  void testFindClassesInDirectory() throws IOException {
    Path jar = getJarWithClass(Test.class);
    Path directory = tempDir.newDirectoryPath("extracted-jar");
    new Decompressor.Zip(jar).extract(directory);

    checkJUnitClasses(jar);
  }

  @SuppressWarnings("SameParameterValue")
  private static @NotNull Path getJarWithClass(@NotNull Class<?> clazz) {
    String resourceRoot = PathManager.getResourceRoot(clazz, "/" + clazz.getName().replace(".", "/") + ".class");
    then(resourceRoot).isNotNull();
    Path path = Path.of(resourceRoot);
    then(path).exists().isRegularFile().hasExtension("jar");
    return path;
  }

  private static void checkJUnitClasses(Path root) {
    String[] conventionallyNamed = {
      org.junit.jupiter.api.DynamicTest.class.getName(),
      org.junit.jupiter.api.RepeatedTest.class.getName(),
      org.junit.jupiter.api.Test.class.getName(),
    };
    String[] unconventionallyNamed = {
      org.junit.jupiter.api.TestMethodOrder.class.getName(),
      org.junit.jupiter.api.TestReporter.class.getName(),
      org.junit.jupiter.api.TestTemplate.class.getName(),
    };
    ClassFinder finder;
    finder = new ClassFinder(root, "", false);
    then(finder.getClasses())
      .contains(conventionallyNamed)
      .doesNotContain(unconventionallyNamed);

    finder = new ClassFinder(root, "org", false);
    then(finder.getClasses())
      .contains(conventionallyNamed)
      .doesNotContain(unconventionallyNamed);

    finder = new ClassFinder(root, "org.junit.jupiter", false);
    then(finder.getClasses())
      .contains(conventionallyNamed)
      .doesNotContain(unconventionallyNamed);

    finder = new ClassFinder(root, "", true);
    then(finder.getClasses())
      .contains(conventionallyNamed)
      .contains(unconventionallyNamed);
  }
}