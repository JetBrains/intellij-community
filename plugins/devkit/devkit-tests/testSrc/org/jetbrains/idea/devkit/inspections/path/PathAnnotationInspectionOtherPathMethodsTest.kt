// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionOtherPathMethodsTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testOtherPathMethods() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class OtherPathMethods {
          public void testMethod() {
              // Create paths
              Path base = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/base/path"</warning>);
              Path other = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/other/path"</warning>);

              // Test Path.relativize(Path) - this should not be highlighted
              Path relativized = base.relativize(other);

              // Test Path.resolveSibling(Path) - this should not be highlighted
              Path resolvedSibling = base.resolveSibling(other);

              // Test Path.startsWith(Path) - this should not be highlighted
              boolean starts = base.startsWith(other);

              // Test Path.endsWith(Path) - this should not be highlighted
              boolean ends = base.endsWith(other);

              // Test Path.compareTo(Path) - this should not be highlighted
              int comparison = base.compareTo(other);
          }
      }      
      """.trimIndent())
  }

  fun testMixedPathMethods() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class MixedPathMethods {
          public void testMethod() {
              // Create paths
              Path base = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/base/path"</warning>);

              // Test Path.resolveSibling(String) - this should be highlighted if not annotated
              Path resolvedSibling1 = base.resolveSibling(<weak_warning descr="String without path annotation is used in Path.resolve() method">"other/path"</weak_warning>);

              // Test Path.resolveSibling(String) with annotated string - this should not be highlighted
              @MultiRoutingFileSystemPath String annotatedPath = "other/path";
              Path resolvedSibling2 = base.resolveSibling(annotatedPath);

              // Test Path.startsWith(String) - this should be highlighted if not annotated
              boolean starts = base.startsWith(<weak_warning descr="String without path annotation is used in Path.resolve() method">"/base"</weak_warning>);

              // Test Path.endsWith(String) - this should be highlighted if not annotated
              boolean ends = base.endsWith(<weak_warning descr="String without path annotation is used in Path.resolve() method">"path/with/slashes"</weak_warning>);
          }
      }      
      """.trimIndent())
  }
}
