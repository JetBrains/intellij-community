// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionJavaTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testFileSystemGetPath() {
    doTest("""
      import com.intellij.platform.eel.annotations.Filename;
      import com.intellij.platform.eel.annotations.NativePath;

      import java.nio.file.FileSystems;
      import java.nio.file.FileSystem;

      public class FileSystemGetPath {
          public void testMethod() {
              FileSystem fs = FileSystems.getDefault();

              // First argument should be annotated with @NativePath
              String nonAnnotatedPath = "/usr/local/bin";
              fs.getPath(<warning descr="${message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")}">nonAnnotatedPath</warning>, "file.txt");

              // First argument with @NativePath is correct
              @NativePath String nativePath = "/usr/local/bin";
              fs.getPath(nativePath, "file.txt");

              // Elements of 'more' parameter should be annotated with either @NativePath or @Filename
              String nonAnnotatedMore = "invalid/path";
              fs.getPath(nativePath, <warning descr="${message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")}">nonAnnotatedMore</warning>);

              // Elements of 'more' parameter with @NativePath is correct
              @NativePath String nativeMore = "subdir";
              fs.getPath(nativePath, nativeMore);

              // Elements of 'more' parameter with @Filename is also correct
              @Filename String filenameMore = "file.txt";
              fs.getPath(nativePath, filenameMore);
          }
      }      
      """.trimIndent())
  }

  fun testNativePathInPathOf() {
    doTest("""
      import com.intellij.platform.eel.annotations.NativePath;
      import java.nio.file.Path;

      public class NativePathInPathOf {
          public void testMethod() {
              @NativePath String nativePath = "/usr/local/bin";
              // This should be highlighted as an error because @NativePath strings should not be used directly in Path.of()
              Path path = Path.of(<warning descr="${message("inspections.message.nativepath.should.not.be.used.directly.constructing.path")}">nativePath</warning>);
          }
      }      
      """.trimIndent())
  }

  fun testMultiRoutingPathInNativeContext() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import com.intellij.platform.eel.annotations.NativeContext;

      public class MultiRoutingPathInNativeContext {
          public void testMethod() {
              @MultiRoutingFileSystemPath String multiRoutingPath = "/home/user/documents";

              // This method expects a @NativePath string
              processNativePath(<warning descr="${message("inspections.message.multiroutingfilesystempath.passed.to.nativepath.method.parameter")}">multiRoutingPath</warning>);
          }

          public void processNativePath(@NativePath String path) {
              // Process the native path
              System.out.println("Processing native path: " + path);
          }

          @NativeContext
          public void nativeContextMethod() {
              @MultiRoutingFileSystemPath String multiRoutingPath = "/home/user/documents";
              // Using a @MultiRoutingFileSystemPath string in a @NativeContext method
              String processedPath = multiRoutingPath + "/file.txt";
          }
      }
      """.trimIndent())
  }

  fun testNonAnnotatedStringInPathOf() {
    doTest("""
      import java.nio.file.Path;

      public class NonAnnotatedStringInPathOf {
          public void testMethod() {
              String nonAnnotatedPath = "/usr/local/bin";
              // This should be highlighted as a normal warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">nonAnnotatedPath</warning>);

              // Direct string literal should also be highlighted
              Path directPath = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/another/path"</warning>);
          }
      }      
      """.trimIndent())
  }

  fun testNonAnnotatedStringInPathResolve() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class NonAnnotatedStringInPathResolve {
          public void testMethod() {
              Path basePath = Paths.get(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/base/path"</warning>);

              String nonAnnotatedPath = "invalid/path";
              // This should be highlighted as a warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = basePath.resolve(<weak_warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">nonAnnotatedPath</weak_warning>);

              // Direct string literal should also be highlighted
              Path directPath = basePath.resolve(<weak_warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">"another/subdir"</weak_warning>);

              // Annotated string should not be highlighted
              @MultiRoutingFileSystemPath String annotatedPath = "annotated/subdir";
              Path correctPath = basePath.resolve(annotatedPath);
          }
      }      
      """.trimIndent())
  }

  fun testFilenameAnnotatedStringInPathResolve() {
    doTest("""
      import com.intellij.platform.eel.annotations.Filename;
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class FilenameAnnotatedStringInPathResolve {
          public void testMethod() {
              Path basePath = Paths.get(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/base/path"</warning>);

              // Non-annotated string should be highlighted
              String nonAnnotatedPath = "invalid/path";
              Path path = basePath.resolve(<weak_warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">nonAnnotatedPath</weak_warning>);

              // String annotated with @Filename should not be highlighted
              @Filename String filenameAnnotatedPath = "file.txt";
              Path filenameAnnotatedPathResolved = basePath.resolve(filenameAnnotatedPath);

              // String annotated with @MultiRoutingFileSystemPath should not be highlighted
              @MultiRoutingFileSystemPath String multiRoutingAnnotatedPath = "annotated/subdir";
              Path multiRoutingAnnotatedPathResolved = basePath.resolve(multiRoutingAnnotatedPath);
          }
      }      
      """.trimIndent())
  }

  fun testPathOfWithMoreParameters() {
    doTest("""
      import com.intellij.platform.eel.annotations.Filename;
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.NativePath;

      import java.nio.file.Path;

      public class PathOfWithMoreParameters {
          public void testMethod() {
              // First argument should be annotated with @MultiRoutingFileSystemPath
              @MultiRoutingFileSystemPath String basePath = "/base/path";

              // Non-annotated string in 'more' parameter should be highlighted
              String nonAnnotatedMore = "invalid/path";
              Path path1 = Path.of(basePath, <warning descr="${message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")}">nonAnnotatedMore</warning>);

              // @NativePath string in 'more' parameter should not be highlighted
              @NativePath String nativeMore = "invalid/path";
              Path path2 = Path.of(basePath, nativeMore);

              // @Filename string in 'more' parameter should not be highlighted
              @Filename String filenameMore = "file.txt";
              Path path3 = Path.of(basePath, filenameMore);

              // @MultiRoutingFileSystemPath string in 'more' parameter should not be highlighted
              @MultiRoutingFileSystemPath String multiRoutingMore = "subdir";
              Path path4 = Path.of(basePath, multiRoutingMore);

              // Multiple 'more' parameters should be checked
              Path path5 = Path.of(basePath, filenameMore, <warning descr="${message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")}">nonAnnotatedMore</warning>, multiRoutingMore);
          }
      }      
      """.trimIndent())
  }
  /**
   * Test that string literals that denote a filename (without slashes) are allowed in places where @Filename is expected.
   */
  fun testStringLiteralFilename() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.NativePath;

      import java.nio.file.FileSystem;
      import java.nio.file.FileSystems;
      import java.nio.file.Path;

      public class StringLiteralFilename {
          public void testMethod() {
              // String literals that denote a filename (without slashes) should be allowed in Path.of() more parameters
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path path1 = Path.of(basePath, "file.txt"); // No warning, "file.txt" is a valid filename
              Path path2 = Path.of(basePath, <warning descr="${message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")}">"invalid/filename"</warning>); // Warning, contains slash

              // String literals that denote a filename should be allowed in FileSystem.getPath() more parameters
              FileSystem fs = FileSystems.getDefault();
              @NativePath String nativePath = "/usr/local/bin";
              fs.getPath(nativePath, "file.txt"); // No warning, "file.txt" is a valid filename
              fs.getPath(nativePath, <warning descr="${message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")}">"invalid/filename"</warning>); // Warning, contains slash

              // String constants that denote a filename should also be allowed
              final String validFilename = "file.txt";
              final String invalidFilename = "invalid/filename";

              Path path3 = Path.of(basePath, validFilename); // No warning, validFilename is a valid filename
              Path path4 = Path.of(basePath, <warning descr="${message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")}">invalidFilename</warning>); // Warning, contains slash

              fs.getPath(nativePath, validFilename); // No warning, validFilename is a valid filename
              fs.getPath(nativePath, <warning descr="${message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")}">invalidFilename</warning>); // Warning, contains slash
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that string literals that denote a filename (without slashes) are allowed in Path.resolve() method.
   */
  fun testPathResolve() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.Filename;
      import java.nio.file.Path;
      import java.nio.file.Paths;

      public class PathResolve {
          public void testMethod() {
              // Create a base path
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path base = Path.of(basePath);

              // Test with string literals
              Path path1 = base.resolve("file.txt"); // No warning, "file.txt" is a valid filename
              Path path2 = base.resolve(<weak_warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">"invalid/filename"</weak_warning>); // Warning, contains slash

              // Test with annotated strings
              @MultiRoutingFileSystemPath String multiRoutingPath = "some/path";
              @Filename String filename = "file.txt";

              Path path3 = base.resolve(multiRoutingPath); // No warning, annotated with @MultiRoutingFileSystemPath
              Path path4 = base.resolve(filename); // No warning, annotated with @Filename

              // Test with string constants
              final String validFilename = "file.txt";
              final String invalidFilename = "invalid/filename";

              Path path5 = base.resolve(validFilename); // No warning, validFilename is a valid filename
              Path path6 = base.resolve(<weak_warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">invalidFilename</weak_warning>); // Warning, contains slash
          }
      }      
      """.trimIndent())
  }

  /**
   * Test that @LocalPath annotations are processed correctly.
   */
  fun testLocalPath() {
    doTest("""
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;
      import com.intellij.platform.eel.annotations.LocalPath;
      import com.intellij.platform.eel.annotations.NativePath;
      import java.nio.file.Path;
      import java.nio.file.FileSystem;
      import java.nio.file.FileSystems;

      class LocalPathTest {
          public void testMethod() {
              // Test @LocalPath in Path.of()
              @LocalPath String localPath = "/local/path";
              Path path1 = Path.of(localPath); // @LocalPath can be used directly in Path.of()

              // Test @LocalPath in Path.resolve()
              @MultiRoutingFileSystemPath String basePath = "/base/path";
              Path base = Path.of(basePath);
              Path path2 = base.resolve(<warning descr="${message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")}">localPath</warning>); // Warning, @LocalPath should not be used in Path.resolve()

              // Test @LocalPath in FileSystem.getPath()
              FileSystem fs = FileSystems.getDefault();
              fs.getPath(<warning descr="${message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")}">localPath</warning>, "file.txt"); // Warning, first argument should be @NativePath
          }
      }      
      """.trimIndent())
  }
}
