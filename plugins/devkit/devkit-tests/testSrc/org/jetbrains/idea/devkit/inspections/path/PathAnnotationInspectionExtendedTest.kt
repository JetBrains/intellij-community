// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the extended functionality of [org.jetbrains.idea.devkit.inspections.path.PathAnnotationInspection].
 * Tests that:
 * 1. The return type of `java.nio.file.Path.toString()` is considered as annotated with `@MultiRoutingFileSystemPath`
 * 2. The return type of `System.getProperty("user.home")` is considered as annotated with `@LocalPath`
 */
class PathAnnotationInspectionExtendedTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  /**
   * Test that the return type of `java.nio.file.Path.toString()` is considered as annotated with `@MultiRoutingFileSystemPath`.
   */
  fun testPathToString() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      import java.nio.file.Path;
      import java.nio.file.Paths;

      class PathToStringTest {
          void testMethod() {
              // Create a Path object
              @MultiRoutingFileSystemPath String somePath = "/some/path";
              Path path = Path.of(somePath);

              // Get the string representation of the path
              String pathString = path.toString();

              // Use the string in Path.of() - should not produce a warning because it's considered as @MultiRoutingFileSystemPath
              Path newPath = Path.of(pathString);

              // Use the string in Path.resolve() - should not produce a warning because it's considered as @MultiRoutingFileSystemPath
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path basePathObj = Path.of(basePath);
              Path resolvedPath = basePathObj.resolve(pathString);

              // Test with the inlined `Path.toString()` calls - this should not produce a warning
              Path inlinedNewPath = Paths.get(path.toString());
              Path inlinedNewPathOf = Path.of(newPath.toString());
              Path inlinedResolvedPath = Paths.get(basePathObj.toString(), "child");
              Path inlinedResolvedPathOf = Path.of(resolvedPath.toString(), "thatChild");
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that the return type of `System.getProperty("user.home")` is considered as annotated with `@LocalPath`.
   */
  fun testSystemGetPropertyUserHome() {
    doTest("""
      import java.nio.file.Path;

      class SystemGetPropertyUserHomeTest {
          void testMethod() {
              // Get the user home directory
              String userHome = System.getProperty("user.home");

              // Use the string in Path.of() - should not produce a warning because it's considered as @LocalPath
              Path homePath = Path.of(userHome);

              // Test with different case
              String userHomeUpperCase = System.getProperty("USER.HOME");
              Path homePathUpperCase = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">userHomeUpperCase</warning>);

              // Test with inlined call - this should not produce a warning
              Path homePathInlinedCall = Path.of(System.getProperty("user.home"));

              Path tempPath = Path.of(System.getProperty("java.io.tmpdir"));
              Path javaHomePath = Path.of(System.getProperty("java.home"));
              Path userDirPath = Path.of(System.getProperty("user.dir"));

              // Test with a different property - should produce a warning
              String javaVersion = System.getProperty("java.version");
              Path questionablePath = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">javaVersion</warning>);
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that both extended features work together.
   */
  fun testCombinedFeatures() {
    doTest("""
      import java.nio.file.Path;
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      class CombinedFeaturesTest {
          void testMethod() {
              // Get the user home directory
              String userHome = System.getProperty("user.home");

              // Create a path from the user home
              Path homePath = Path.of(userHome);

              // Get the string representation of the path
              String homePathString = homePath.toString();

              // Use the string in Path.of() - should not produce a warning
              Path newHomePath = Path.of(homePathString);

              // Use the string in Path.resolve() - should not produce a warning
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path basePathObj = Path.of(basePath);
              Path resolvedPath = basePathObj.resolve(homePathString);
          }
      }      
      """.trimIndent())
  }
}
