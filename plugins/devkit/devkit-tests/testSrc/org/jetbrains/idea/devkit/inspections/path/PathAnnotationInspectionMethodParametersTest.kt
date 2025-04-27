// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the PathAnnotationInspection that verify proper handling of method parameters with path annotations.
 * This test class covers the following cases:
 * - @MultiRoutingFileSystemPath parameters accepting @MultiRoutingFileSystemPath, @LocalPath, @Filename values
 * - @NativePath parameters accepting @NativePath, @Filename values
 * - @LocalPath parameters accepting @LocalPath, @Filename values
 * - @Filename parameters accepting @Filename values
 */
class PathAnnotationInspectionMethodParametersTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  /**
   * Test that @MultiRoutingFileSystemPath parameters accept @MultiRoutingFileSystemPath, @LocalPath, and @Filename values.
   */
  fun testMultiRoutingFileSystemPathParameters() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class MultiRoutingFileSystemPathParameters {
          public void testMethod() {
              // Test with @MultiRoutingFileSystemPath values
              @MultiRoutingFileSystemPath String multiRoutingPath = "/path/to/file";
              processMultiRoutingPath(multiRoutingPath); // Should be accepted

              // Test with @LocalPath values
              @LocalPath String localPath = "/local/path";
              processMultiRoutingPath(localPath); // Should be accepted

              // Test with @Filename values
              @Filename String filename = "file.txt";
              processMultiRoutingPath(filename); // Should be accepted

              // Test with @NativePath values
              @NativePath String nativePath = "/native/path";
              processMultiRoutingPath(<warning descr="${message("inspections.message.nativepath.passed.to.multiroutingfilesystempath.method.parameter")}">nativePath</warning>); // Should be warned

              // Test with string literals
              processMultiRoutingPath(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/unannotated/path"</warning>); // Should be warned
              processMultiRoutingPath("file.txt"); // Should be accepted as it's a valid filename
          }

          public void processMultiRoutingPath(@MultiRoutingFileSystemPath String path) {
              System.out.println("Processing path: " + path);
          }
      }
      """.trimIndent())
  }

  /**
   * Test that @NativePath parameters accept @NativePath and @Filename values.
   */
  fun testNativePathParameters() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class NativePathParameters {
          public void testMethod() {
              // Test with @NativePath values
              @NativePath String nativePath = "/native/path";
              processNativePath(nativePath); // Should be accepted

              // Test with @Filename values
              @Filename String filename = "file.txt";
              processNativePath(filename); // Should be accepted

              // Test with @MultiRoutingFileSystemPath values
              @MultiRoutingFileSystemPath String multiRoutingPath = "/path/to/file";
              processNativePath(<warning descr="${message("inspections.message.multiroutingfilesystempath.passed.to.nativepath.method.parameter")}">multiRoutingPath</warning>); // Should be warned

              // Test with @LocalPath values
              @LocalPath String localPath = "/local/path";
              processNativePath(<warning descr="${message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")}">localPath</warning>); // Should be warned

              // Test with string literals
              processNativePath(<warning descr="${message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")}">"/unannotated/path"</warning>); // Should be warned
              processNativePath("file.txt"); // Should be accepted as it's a valid filename
          }

          public void processNativePath(@NativePath String path) {
              System.out.println("Processing native path: " + path);
          }
      }
      """.trimIndent())
  }

  /**
   * Test that @LocalPath parameters accept @LocalPath and @Filename values.
   */
  fun testLocalPathParameters() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class LocalPathParameters {
          public void testMethod() {
              // Test with @LocalPath values
              @LocalPath String localPath = "/local/path";
              processLocalPath(localPath); // Should be accepted

              // Test with @Filename values
              @Filename String filename = "file.txt";
              processLocalPath(filename); // Should be accepted

              // Test with @MultiRoutingFileSystemPath values
              @MultiRoutingFileSystemPath String multiRoutingPath = "/path/to/file";
              processLocalPath(multiRoutingPath); // Should be warned (currently not implemented)

              // Test with @NativePath values
              @NativePath String nativePath = "/native/path";
              processLocalPath(nativePath); // Should be warned (currently not implemented)

              // Test with string literals
              processLocalPath("/unannotated/path"); // Should be warned (currently not implemented)
              processLocalPath("file.txt"); // Should be accepted as it's a valid filename
          }

          public void processLocalPath(@LocalPath String path) {
              System.out.println("Processing local path: " + path);
          }
      }
      """.trimIndent())
  }

  /**
   * Test that @Filename parameters accept @Filename values.
   */
  fun testFilenameParameters() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class FilenameParameters {
          public void testMethod() {
              // Test with @Filename values
              @Filename String filename = "file.txt";
              processFilename(filename); // Should be accepted

              // Test with @MultiRoutingFileSystemPath values
              @MultiRoutingFileSystemPath String multiRoutingPath = "/path/to/file";
              processFilename(multiRoutingPath); // Should be warned (currently not implemented)

              // Test with @LocalPath values
              @LocalPath String localPath = "/local/path";
              processFilename(localPath); // Should be warned (currently not implemented)

              // Test with @NativePath values
              @NativePath String nativePath = "/native/path";
              processFilename(nativePath); // Should be warned (currently not implemented)

              // Test with string literals
              processFilename("/unannotated/path"); // Should be warned (currently not implemented)
              processFilename("file.txt"); // Should be accepted as it's a valid filename
          }

          public void processFilename(@Filename String filename) {
              System.out.println("Processing filename: " + filename);
          }
      }
      """.trimIndent())
  }
}
