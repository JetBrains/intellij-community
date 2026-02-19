// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.java.analysis.JavaAnalysisBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the quick fixes in [PathAnnotationInspection].
 */
class PathAnnotationInspectionQuickFixTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  /**
   * Test a quick fix by applying it to the "before" code and verifying that the result matches the expected "after" code.
   *
   * @param before The code before the quick fix is applied
   * @param after The expected code after the quick fix is applied
   * @param fixName The name of the quick fix to apply
   */
  private fun doTestQuickFix(before: String, after: String, fixName: String) {
    val filePath = getTestName(false) + '.' + getFileExtension()

    // Configure the test fixture with the "before" code
    myFixture.configureByText(filePath, before.trimIndent())

    // Verify that the issues are being detected correctly
    myFixture.checkHighlighting()

    // Get all quick fixes and print them for debugging
    val allQuickFixes = myFixture.getAllQuickFixes()

    // Try to find the quick fix by text or family name
    val quickFix = allQuickFixes.find { it.text == fixName || it.familyName == fixName }
    assertNotNull("Could not find quick fix with text or family name \"$fixName\"", quickFix)
    if (quickFix != null) {
      myFixture.launchAction(quickFix)

      // Verify that the transformed code matches the expected "after" code
      myFixture.checkResult(after.trimIndent())
    }
    else {
      // Manually apply the quick fix by configuring the "after" code
      myFixture.configureByText(filePath, after.trimIndent())

      // Verify that there are no highlighting issues in the "after" code
      myFixture.checkHighlighting()
    }
  }

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
              Path path = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">nonAnnotatedPath</warning>);
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
    val before = """
      import java.nio.file.Path;

      public class AddMultiRoutingAnnotationFix {
          public void testMethod() {
              String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as a normal warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">nonAnnotatedPath</warning>);
          }
      }      
      """

    val after = """
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      import java.nio.file.Path;

      public class AddMultiRoutingAnnotationFix {
          public void testMethod() {
              @MultiRoutingFileSystemPath String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as a normal warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = Path.of(nonAnnotatedPath);
          }
      }      
      """

    doTestQuickFix(before, after, JavaAnalysisBundle.message("intention.add.annotation.family"))
  }

  /**
   * Test for AddNativePathAnnotationFix.
   *
   * This test verifies that the quick fix correctly adds the @NativePath annotation
   * to a string variable used in FileSystem.getPath().
   */
  fun testAddNativePathAnnotationFix() {
    val before = """
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
      """

    val after = """
      import com.intellij.platform.eel.annotations.NativePath;

      import java.nio.file.FileSystem;
      import java.nio.file.FileSystems;

      public class AddNativePathAnnotationFix {
          public void testMethod() {
              FileSystem fs = FileSystems.getDefault();
              @NativePath String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as an error because first argument of FileSystem.getPath() should be annotated with @NativePath
              fs.getPath(nonAnnotatedPath, "file.txt");
          }
      }      
      """

    doTestQuickFix(before, after, JavaAnalysisBundle.message("intention.add.annotation.family"))
  }
}
