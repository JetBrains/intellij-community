// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionPathOfTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testPathOfWithAnnotatedFirstArgAndLiteralFilenames() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;

      public class PathOfWithAnnotatedFirstArgAndLiteralFilenames {
          public void testMethod() {
              // First argument is correctly annotated with @MultiRoutingFileSystemPath
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              
              // Other arguments are literal filenames without path separators - this should be OK
              Path path1 = Path.of(basePath, "file.txt");
              Path path2 = Path.of(basePath, "file.txt", "another.txt");
              Path path3 = Path.of(basePath, "file.txt", "dir", "another.txt");
              
              // String constants that are valid filenames should also be OK
              final String validFilename1 = "file.txt";
              final String validFilename2 = "another.txt";
              Path path4 = Path.of(basePath, validFilename1, validFilename2);

              Path path10 = Path.of("hello", "world");
              
              final String validDirectoryName = "hello";
              Path path11 = Path.of(validDirectoryName, "world");
          }
      }      
      """.trimIndent())
  }

  fun testPathOfWithNonAnnotatedFirstArg() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.Filename;
      import java.nio.file.Path;

      public class PathOfWithNonAnnotatedFirstArg {
          public void testMethod() {
              // First argument is NOT correctly annotated - this should be highlighted
              String nonAnnotatedPath = "/base/path";
              Path path1 = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">nonAnnotatedPath</warning>, "file.txt");
              
              // Even if other arguments are correctly annotated, it should still be highlighted
              @Filename String filename = "file.txt";
              @MultiRoutingFileSystemPath String subdir = "subdir";
              Path path2 = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">nonAnnotatedPath</warning>, filename, subdir);
              
              // Direct string literal as first argument should also be highlighted
              Path path3 = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/another/path"</warning>, "file.txt");
          }
      }      
      """.trimIndent())
  }
}