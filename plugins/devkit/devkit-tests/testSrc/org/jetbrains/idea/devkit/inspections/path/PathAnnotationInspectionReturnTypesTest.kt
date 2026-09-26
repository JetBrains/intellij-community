// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

/**
 * Tests for the PathAnnotationInspection that verify proper handling of method return types with path annotations.
 * This test class covers the following cases:
 * - Methods returning @MultiRoutingFileSystemPath should return @MultiRoutingFileSystemPath, @LocalPath, or @Filename values
 * - Methods returning @NativePath should return @NativePath or @Filename values
 * - Methods returning @LocalPath should return @LocalPath or @Filename values
 * - Methods returning @Filename should return @Filename values
 */
class PathAnnotationInspectionReturnTypesTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  /**
   * Test that methods returning @MultiRoutingFileSystemPath return appropriate values.
   */
  fun testMultiRoutingFileSystemPathReturnType() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class MultiRoutingFileSystemPathReturnType {
          // Return @MultiRoutingFileSystemPath value
          @MultiRoutingFileSystemPath
          public String getMultiRoutingPath() {
              @MultiRoutingFileSystemPath String path = "/path/to/file";
              return path; // Should be accepted
          }

          // Return @LocalPath value
          @MultiRoutingFileSystemPath
          public String getLocalPathAsMultiRouting() {
              @LocalPath String path = "/local/path";
              return path; // Should be accepted
          }

          // Return @Filename value
          @MultiRoutingFileSystemPath
          public String getFilenameAsMultiRouting() {
              @Filename String filename = "file.txt";
              return filename; // Should be accepted
          }

          // Return @NativePath value
          @MultiRoutingFileSystemPath
          public String getNativePathAsMultiRouting() {
              @NativePath String path = "/native/path";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@MultiRoutingFileSystemPath", "@NativePath")}">path</warning>; // Should be warned
          }

          // Return unannotated value
          @MultiRoutingFileSystemPath
          public String getUnannotatedAsMultiRouting() {
              String path = "/unannotated/path";
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@MultiRoutingFileSystemPath")}">path</warning>; // Should be warned
          }

          // Return string literal
          @MultiRoutingFileSystemPath
          public String getStringLiteralAsMultiRouting() {
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@MultiRoutingFileSystemPath")}">"/string/literal"</warning>; // Should be warned
          }

          // Return filename string literal
          @MultiRoutingFileSystemPath
          public String getFilenameStringLiteralAsMultiRouting() {
              return "file.txt"; // Should be accepted as it's a valid filename
          }
      }
      """.trimIndent())
  }

  /**
   * Test that methods returning @NativePath return appropriate values.
   */
  fun testNativePathReturnType() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class NativePathReturnType {
          // Return @NativePath value
          @NativePath
          public String getNativePath() {
              @NativePath String path = "/native/path";
              return path; // Should be accepted
          }

          // Return @Filename value
          @NativePath
          public String getFilenameAsNativePath() {
              @Filename String filename = "file.txt";
              return filename; // Should be accepted
          }

          // Return @MultiRoutingFileSystemPath value
          @NativePath
          public String getMultiRoutingPathAsNativePath() {
              @MultiRoutingFileSystemPath String path = "/path/to/file";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@NativePath", "@MultiRoutingFileSystemPath")}">path</warning>; // Should be warned
          }

          // Return @LocalPath value
          @NativePath
          public String getLocalPathAsNativePath() {
              @LocalPath String path = "/local/path";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@NativePath", "@LocalPath")}">path</warning>; // Should be warned
          }

          // Return unannotated value
          @NativePath
          public String getUnannotatedAsNativePath() {
              String path = "/unannotated/path";
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@NativePath")}">path</warning>; // Should be warned
          }

          // Return string literal
          @NativePath
          public String getStringLiteralAsNativePath() {
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@NativePath")}">"/string/literal"</warning>; // Should be warned
          }

          // Return filename string literal
          @NativePath
          public String getFilenameStringLiteralAsNativePath() {
              return "file.txt"; // Should be accepted as it's a valid filename
          }
      }
      """.trimIndent())
  }

  /**
   * Test that methods returning @LocalPath return appropriate values.
   */
  fun testLocalPathReturnType() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class LocalPathReturnType {
          // Return @LocalPath value
          @LocalPath
          public String getLocalPath() {
              @LocalPath String path = "/local/path";
              return path; // Should be accepted
          }

          // Return @Filename value
          @LocalPath
          public String getFilenameAsLocalPath() {
              @Filename String filename = "file.txt";
              return filename; // Should be accepted
          }

          // Return @MultiRoutingFileSystemPath value
          @LocalPath
          public String getMultiRoutingPathAsLocalPath() {
              @MultiRoutingFileSystemPath String path = "/path/to/file";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@LocalPath", "@MultiRoutingFileSystemPath")}">path</warning>; // Should be warned
          }

          // Return @NativePath value
          @LocalPath
          public String getNativePathAsLocalPath() {
              @NativePath String path = "/native/path";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@LocalPath", "@NativePath")}">path</warning>; // Should be warned
          }

          // Return unannotated value
          @LocalPath
          public String getUnannotatedAsLocalPath() {
              String path = "/unannotated/path";
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@LocalPath")}">path</warning>; // Should be warned
          }

          // Return string literal
          @LocalPath
          public String getStringLiteralAsLocalPath() {
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@LocalPath")}">"/string/literal"</warning>; // Should be warned
          }

          // Return filename string literal
          @LocalPath
          public String getFilenameStringLiteralAsLocalPath() {
              return "file.txt"; // Should be accepted as it's a valid filename
          }
      }
      """.trimIndent())
  }

  /**
   * Test that methods returning @Filename return appropriate values.
   */
  fun testFilenameReturnType() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.Filename;

      public class FilenameReturnType {
          // Return @Filename value
          @Filename
          public String getFilename() {
              @Filename String filename = "file.txt";
              return filename; // Should be accepted
          }

          // Return @MultiRoutingFileSystemPath value
          @Filename
          public String getMultiRoutingPathAsFilename() {
              @MultiRoutingFileSystemPath String path = "/path/to/file";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@Filename", "@MultiRoutingFileSystemPath")}">path</warning>; // Should be warned
          }

          // Return @LocalPath value
          @Filename
          public String getLocalPathAsFilename() {
              @LocalPath String path = "/local/path";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@Filename", "@LocalPath")}">path</warning>; // Should be warned
          }

          // Return @NativePath value
          @Filename
          public String getNativePathAsFilename() {
              @NativePath String path = "/native/path";
              return <warning descr="${message("inspection.message.method.annotated.with.returns.value.annotated.with", "@Filename", "@NativePath")}">path</warning>; // Should be warned
          }

          // Return unannotated value
          @Filename
          public String getUnannotatedAsFilename() {
              String path = "/unannotated/path";
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@Filename")}">path</warning>; // Should be warned
          }

          // Return string literal with path
          @Filename
          public String getStringLiteralAsFilename() {
              return <warning descr="${message("inspection.message.return.value.without.path.annotation.where.expected", "@Filename")}">"/string/literal"</warning>; // Should be warned
          }

          // Return filename string literal
          @Filename
          public String getFilenameStringLiteralAsFilename() {
              return "file.txt"; // Should be accepted as it's a valid filename
          }
      }
      """.trimIndent())
  }
}
