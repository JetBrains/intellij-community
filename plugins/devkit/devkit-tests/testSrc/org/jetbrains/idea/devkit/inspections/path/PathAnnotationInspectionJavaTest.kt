// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase

class PathAnnotationInspectionJavaTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "java"

  fun testNativePathInPathOf() {
    doTest("""
      import com.intellij.platform.eel.annotations.NativePath;
      import java.nio.file.Path;

      public class NativePathInPathOf {
          public void testMethod() {
              @NativePath String nativePath = "/usr/local/bin";
              // This should be highlighted as an error because @NativePath strings should not be used directly in Path.of()
              Path path = <warning descr="A string annotated with @NativePath should not be used directly in Path constructor or factory method">Path.of(nativePath)</warning>;
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
              processNativePath(<warning descr="A string annotated with @MultiRoutingFileSystemPath is passed to a method parameter annotated with @NativePath">multiRoutingPath</warning>);
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
              Path path = <warning descr="String without path annotation is used in Path constructor or factory method">Path.of(nonAnnotatedPath)</warning>;

              // Direct string literal should also be highlighted
              Path directPath = <warning descr="String without path annotation is used in Path constructor or factory method">Path.of(<warning descr="String literal is used in a context that expects @MultiRoutingFileSystemPath">"/another/path"</warning>)</warning>;
          }
      }      
      """.trimIndent())
  }

  fun testNonAnnotatedStringInPathResolve() {
    doTest("""
      import java.nio.file.Path;
      import java.nio.file.Paths;
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath;

      public class NonAnnotatedStringInPathResolve {
          public void testMethod() {
              Path basePath = <warning descr="String without path annotation is used in Path constructor or factory method">Paths.get(<warning descr="String literal is used in a context that expects @MultiRoutingFileSystemPath">"/base/path"</warning>)</warning>;

              String nonAnnotatedPath = "subdir";
              // This should be highlighted as a warning because non-annotated strings should be annotated with @MultiRoutingFileSystemPath
              Path path = <warning descr="String without path annotation is used in Path constructor or factory method">basePath.resolve(nonAnnotatedPath)</warning>;

              // Direct string literal should also be highlighted
              Path directPath = <warning descr="String without path annotation is used in Path constructor or factory method">basePath.resolve(<warning descr="String literal is used in a context that expects @MultiRoutingFileSystemPath">"another/subdir"</warning>)</warning>;

              // Annotated string should not be highlighted
              @MultiRoutingFileSystemPath String annotatedPath = "annotated/subdir";
              Path correctPath = basePath.resolve(annotatedPath);
          }
      }      
      """.trimIndent())
  }
}
