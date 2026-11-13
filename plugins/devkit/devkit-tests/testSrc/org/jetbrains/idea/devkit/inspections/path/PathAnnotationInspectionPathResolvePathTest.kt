// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.pathAnnotationInspectionFixture
import org.junit.jupiter.api.Test

@TestApplication
class PathAnnotationInspectionPathResolvePathTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_17)
  }

  private val pathAnnotation by pathAnnotationInspectionFixture(projectFixture)

  @Test
  fun testPathResolvePath() {
    pathAnnotation.doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class PathResolvePath {
          public void testMethod() {
              // Create a base path
              Path base = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/base/path"</warning>);

              // Create another path
              Path otherPath = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/other/path"</warning>);

              // Test Path.resolve(Path) - this should not be highlighted
              Path resolvedPath = base.resolve(otherPath);

              // Test with a method that returns a Path
              Path resolvedPath2 = base.resolve(getPath());
          }

          private Path getPath() {
              return Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/some/path"</warning>);
          }
      }      
      """.trimIndent())
  }

}
