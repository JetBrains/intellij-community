// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionPathResolvePathTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testPathResolvePath() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class PathResolvePath {
          public void testMethod() {
              // Create a base path
              Path base = <warning descr="String without path annotation is used in Path constructor or factory method">Path.of("/base/path")</warning>;

              // Create another path
              Path otherPath = <warning descr="String without path annotation is used in Path constructor or factory method">Path.of("/other/path")</warning>;

              // Test Path.resolve(Path) - this should not be highlighted
              Path resolvedPath = base.resolve(otherPath);

              // Test with a method that returns a Path
              Path resolvedPath2 = base.resolve(getPath());
          }

          private Path getPath() {
              return <warning descr="String without path annotation is used in Path constructor or factory method">Path.of("/some/path")</warning>;
          }
      }      
      """.trimIndent())
  }

  fun testMixedParameterTypes() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;

      public class MixedParameterTypes {
          public void testMethod(Path path, @MultiRoutingFileSystemPath String pathStr) {
              // This should not be highlighted as the parameter is of type Path
              processPath(path);

              // This should be highlighted as the parameter is of type String
              processPathString(pathStr);
          }

          private void processPath(Path path) {
              // Process the path
              System.out.println("Processing path: " + path);
          }

          private void processPathString(@MultiRoutingFileSystemPath String path) {
              // Process the path string
              System.out.println("Processing path string: " + path);
          }
      }      
      """.trimIndent())
  }
}
