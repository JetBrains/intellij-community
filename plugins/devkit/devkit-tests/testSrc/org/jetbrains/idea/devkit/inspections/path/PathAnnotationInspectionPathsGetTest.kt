// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionPathsGetTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testPathsGetWithAnnotatedFirstArgAndLiteralFilenames() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Paths;
      import java.nio.file.Path;

      public class PathsGetWithAnnotatedFirstArgAndLiteralFilenames {
          public void testMethod() {
              // First argument is correctly annotated with @MultiRoutingFileSystemPath
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              
              // Other arguments are literal filenames without path separators - this should be OK
              Path path1 = Paths.get(basePath, "file.txt");
              Path path2 = Paths.get(basePath, "file.txt", "another.txt");
              Path path3 = Paths.get(basePath, "file.txt", "dir", "another.txt");
              
              // String constants that are valid filenames should also be OK
              final String validFilename1 = "file.txt";
              final String validFilename2 = "another.txt";
              Path path4 = Paths.get(basePath, validFilename1, validFilename2);
          }
      }      
      """.trimIndent())
  }

  fun testPathsGetWithNonAnnotatedFirstArg() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.Filename;
      import java.nio.file.Paths;
      import java.nio.file.Path;

      public class PathsGetWithNonAnnotatedFirstArg {
          public void testMethod() {
              // First argument is NOT correctly annotated - this should be highlighted
              String nonAnnotatedPath = "/base/path";
              Path path1 = <warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.constructor.or.factory.method")}">Paths.get(nonAnnotatedPath, "file.txt")</warning>;
              
              // Even if other arguments are correctly annotated, it should still be highlighted
              @Filename String filename = "file.txt";
              @MultiRoutingFileSystemPath String subdir = "subdir";
              Path path2 = <warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.constructor.or.factory.method")}">Paths.get(nonAnnotatedPath, filename, subdir)</warning>;
              
              // Direct string literal as first argument should also be highlighted
              Path path3 = <warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.constructor.or.factory.method")}">Paths.get("/another/path", "file.txt")</warning>;
          }
      }      
      """.trimIndent())
  }
}