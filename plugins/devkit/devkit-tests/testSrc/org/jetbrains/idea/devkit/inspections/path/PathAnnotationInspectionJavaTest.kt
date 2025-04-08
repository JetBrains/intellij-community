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
}