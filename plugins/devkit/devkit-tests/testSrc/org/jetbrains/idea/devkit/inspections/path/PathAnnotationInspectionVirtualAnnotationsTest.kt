// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the virtual path annotation registry functionality of [org.jetbrains.idea.devkit.inspections.path.PathAnnotationInspection].
 * Tests that:
 * 1. Methods from PathManager that effectively return `@LocalPath` annotated strings:
 *    - getHomePath()
 *    - getBinPath()
 *    - getConfigPath()
 *    - getSystemPath()
 * 2. Methods from SystemProperties that effectively return `@LocalPath` annotated strings:
 *    - getUserHome()
 *    - getJavaHome()
 * 3. Methods from PathManager that effectively accept `@LocalPath` annotated strings:
 *    - isUnderHomeDirectory()
 */
class PathAnnotationInspectionVirtualAnnotationsTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  override fun setUp() {
    super.setUp()
    addMockClasses()
  }

  private fun addMockClasses() {
    // Add mock implementation of PathManager
    myFixture.addClass(
      """
        package com.intellij.openapi.application;

        public class PathManager {
          public static String getHomePath() {
            return "/home/user/idea";
          }

          public static String getBinPath() {
            return "/home/user/idea/bin";
          }

          public static String getConfigPath() {
            return "/home/user/.config/idea";
          }

          public static String getSystemPath() {
            return "/home/user/.cache/idea";
          }

          public static boolean isUnderHomeDirectory(String path) {
            return path.startsWith("/home/user/idea");
          }
        }
      """.trimIndent()
    )

    // Add mock implementation of SystemProperties
    myFixture.addClass(
      """
        package com.intellij.util;

        public class SystemProperties {
          public static String getUserHome() {
            return "/home/user";
          }

          public static String getJavaHome() {
            return "/home/user/jdk";
          }
        }
      """.trimIndent()
    )
  }

  /**
   * Test that methods from PathManager that effectively return `@LocalPath` annotated strings.
   */
  fun testPathManagerReturningLocalPath() {
    doTest("""
      import com.intellij.openapi.application.PathManager;
      import java.nio.file.Path;

      class PathManagerReturningLocalPathTest {
          void testMethod() {
              // Test PathManager.getHomePath()
              String homePath = PathManager.getHomePath();
              Path homePathObj = Path.of(homePath); // Should not produce a warning

              // Test PathManager.getBinPath()
              String binPath = PathManager.getBinPath();
              Path binPathObj = Path.of(binPath); // Should not produce a warning

              // Test PathManager.getConfigPath()
              String configPath = PathManager.getConfigPath();
              Path configPathObj = Path.of(configPath); // Should not produce a warning

              // Test PathManager.getSystemPath()
              String systemPath = PathManager.getSystemPath();
              Path systemPathObj = Path.of(systemPath); // Should not produce a warning

              // Test with inlined calls
              Path inlinedHomePath = Path.of(PathManager.getHomePath());
              Path inlinedBinPath = Path.of(PathManager.getBinPath());
              Path inlinedConfigPath = Path.of(PathManager.getConfigPath());
              Path inlinedSystemPath = Path.of(PathManager.getSystemPath());
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that methods from SystemProperties that effectively return `@LocalPath` annotated strings.
   */
  fun testSystemPropertiesReturningLocalPath() {
    doTest("""
      import com.intellij.util.SystemProperties;
      import java.nio.file.Path;

      class SystemPropertiesReturningLocalPathTest {
          void testMethod() {
              // Test SystemProperties.getUserHome()
              String userHome = SystemProperties.getUserHome();
              Path userHomePath = Path.of(userHome); // Should not produce a warning

              // Test SystemProperties.getJavaHome()
              String javaHome = SystemProperties.getJavaHome();
              Path javaHomePath = Path.of(javaHome); // Should not produce a warning

              // Test with inlined calls
              Path inlinedUserHomePath = Path.of(SystemProperties.getUserHome());
              Path inlinedJavaHomePath = Path.of(SystemProperties.getJavaHome());
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that methods from PathManager that effectively accept `@LocalPath` annotated strings.
   */
  fun testPathManagerAcceptingLocalPath() {
    doTest("""
      import com.intellij.openapi.application.PathManager;
      import java.nio.file.Path;

      class PathManagerAcceptingLocalPathTest {
          void testMethod() {
              // Test PathManager.isUnderHomeDirectory()
              String somePath = "/some/path";
              boolean isUnderHome = PathManager.isUnderHomeDirectory(somePath); // Should not produce a warning

              // Test with a path that is not annotated with @LocalPath
              String randomPath = "random/path";
              boolean isUnderHome2 = PathManager.isUnderHomeDirectory(randomPath); // Should not produce a warning

              // Test with a string literal
              boolean isUnderHome3 = PathManager.isUnderHomeDirectory("/another/path"); // Should not produce a warning
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that methods from PathManager and SystemProperties work together with other path-related functionality.
   */
  fun testCombinedFunctionality() {
    doTest("""
      import com.intellij.openapi.application.PathManager;
      import com.intellij.util.SystemProperties;
      import java.nio.file.Path;
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      class CombinedFunctionalityTest {
          void testMethod() {
              // Get paths from PathManager and SystemProperties
              String homePath = PathManager.getHomePath();
              String userHome = SystemProperties.getUserHome();

              // Create Path objects
              Path homePathObj = Path.of(homePath);
              Path userHomeObj = Path.of(userHome);

              // Use the paths in Path.resolve()
              @MultiRoutingFileSystemPath String childPath = "child";
              Path resolvedHomePath = homePathObj.resolve(childPath);
              Path resolvedUserHomePath = userHomeObj.resolve(childPath);

              // Check if paths are under home directory
              boolean isHomePathUnderHome = PathManager.isUnderHomeDirectory(homePath);
              boolean isUserHomeUnderHome = PathManager.isUnderHomeDirectory(userHome);
              boolean isResolvedHomePathUnderHome = PathManager.isUnderHomeDirectory(resolvedHomePath.toString());
              boolean isResolvedUserHomeUnderHome = PathManager.isUnderHomeDirectory(resolvedUserHomePath.toString());
          }
      }      
      """.trimIndent())
  }
}
