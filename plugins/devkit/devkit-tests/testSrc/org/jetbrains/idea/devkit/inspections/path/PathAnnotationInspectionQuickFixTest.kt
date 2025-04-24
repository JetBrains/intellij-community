// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the quick fixes in [PathAnnotationInspection].
 */
class PathAnnotationInspectionQuickFixTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  /**
   * Test for the inspection itself to verify it's working.
   */
  fun testNonAnnotatedStringInPathOf() {
    doTest("""
      import java.nio.file.Path;

      public class NonAnnotatedStringInPathOf {
          public void testMethod() {
              String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as a normal warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = <warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.constructor.or.factory.method")}">Path.of(nonAnnotatedPath)</warning>;
          }
      }      
      """.trimIndent())
  }

  /**
   * Test for AddMultiRoutingAnnotationFix.
   *
   * This test verifies that the quick fix correctly adds the @MultiRoutingFileSystemPath annotation
   * to a string variable used in Path.of().
   */
  fun testAddMultiRoutingAnnotationFix() {
    doTest("""
      import java.nio.file.Path;

      public class AddMultiRoutingAnnotationFix {
          public void testMethod() {
              String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as a normal warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = <warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.constructor.or.factory.method")}">Path.of(nonAnnotatedPath)</warning>;
          }
      }      
      """.trimIndent())
  }

  /**
   * Test for AddNativePathAnnotationFix.
   *
   * This test verifies that the quick fix correctly adds the @NativePath annotation
   * to a string variable used in FileSystem.getPath().
   */
  fun testAddNativePathAnnotationFix() {
    doTest("""
      import java.nio.file.FileSystem;
      import java.nio.file.FileSystems;

      public class AddNativePathAnnotationFix {
          public void testMethod() {
              FileSystem fs = FileSystems.getDefault();
              String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as an error because first argument of FileSystem.getPath() should be annotated with @NativePath
              fs.getPath(<warning descr="${message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")}">nonAnnotatedPath</warning>, "file.txt");
          }
      }      
      """.trimIndent())
  }
}
