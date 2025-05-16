// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.path

import org.intellij.lang.annotations.Language

/**
 * Tests for PathAnnotationInspection with Kotlin extension methods from kotlin.io.path.
 *
 * According to the requirements:
 * - `Path.name`, `Path.nameWithoutExtension`, `Path.extension` should be considered as returning `@Filename`
 * - `Path.pathString`, `Path.absolutePathString()` should be considered as returning `@MultiRoutingFileSystemPath`
 */
class PathAnnotationInspectionKotlinExtensionMethodsTest : PathAnnotationInspectionKotlinTestBase() {
  fun testPathNameExtensionMethod() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.name
      
      class PathNameExtensionMethodTest {
          fun testMethod(path: Path) {
              // Path.name should be considered as returning @Filename
              val name = path.name

              // TODO: add comment for the next line
              val filenamePath = Path.of(name)

              // Using name in a context that expects @Filename should be allowed
              @Filename val filename: String = name // No warning

              // TODO: add comment for the next line
              @MultiRoutingFileSystemPath val multiRoutingPath: String = name
          }
      }      
      """)
  }

  fun testPathNameWithoutExtensionExtensionMethod() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.nameWithoutExtension
      
      class PathNameWithoutExtensionExtensionMethodTest {
          fun testMethod(path: Path) {
              // Path.nameWithoutExtension should be considered as returning @Filename
              val nameWithoutExtension = path.nameWithoutExtension

              // TODO: add comment for the next line
              val anotherFilenamePathWithoutExtension = Path.of(nameWithoutExtension)

              // Using nameWithoutExtension in a context that expects @Filename should be allowed
              @Filename val filename: String = nameWithoutExtension // No warning
              
              // TODO: add comment for the next line
              @MultiRoutingFileSystemPath val multiRoutingPath: String = nameWithoutExtension
          }
      }      
      """)
  }

  fun testPathExtensionExtensionMethod() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.extension
      
      class PathExtensionExtensionMethodTest {
          fun testMethod(path: Path) {
              // Path.extension should be considered as returning @Filename
              val extension = path.extension

              // TODO: add comment for the next line
              val extensionPath = Path.of(extension)

              // Using extension in a context that expects @Filename should be allowed
              @Filename val filename: String = extension // No warning
              
              // TODO: add comment for the next line
              @MultiRoutingFileSystemPath val multiRoutingPath: String = extension
          }
      }      
      """)
  }

  fun testPathPathStringExtensionMethod() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.pathString
      
      class PathPathStringExtensionMethodTest {
          fun testMethod(path: Path) {
              // Path.pathString should be considered as returning @MultiRoutingFileSystemPath
              val pathString = path.pathString

              // TODO: add comment for the next line
              val samePath = Path.of(pathString)

              // Using pathString in a context that expects @MultiRoutingFileSystemPath should be allowed
              @MultiRoutingFileSystemPath val multiRoutingPath: String = pathString // No warning
              
              // TO BE IMPLEMENTED: Using pathString in a context that expects @Filename should cause a warning
              @Filename val filename: String = pathString
          }
      }      
      """)
  }

  fun testPathAbsolutePathStringExtensionMethod() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.absolutePathString
      
      class PathAbsolutePathStringExtensionMethodTest {
          fun testMethod(path: Path) {
              // Path.absolutePathString() should be considered as returning @MultiRoutingFileSystemPath
              val absolutePathString = path.absolutePathString()

              // TODO: add comment for the next line
              val anotherAbsolutePath = Path.of(absolutePathString)

              // Using absolutePathString in a context that expects @MultiRoutingFileSystemPath should be allowed
              @MultiRoutingFileSystemPath val multiRoutingPath: String = absolutePathString // No warning
              
              // TO BE IMPLEMENTED: Using absolutePathString in a context that expects @Filename should cause a warning
              @Filename val filename: String = absolutePathString
          }
      }      
      """)
  }

  fun testPathExtensionMethodsInPathOf() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import java.nio.file.Path
      import kotlin.io.path.name
      import kotlin.io.path.nameWithoutExtension
      import kotlin.io.path.extension
      import kotlin.io.path.pathString
      import kotlin.io.path.absolutePathString
      
      class PathExtensionMethodsInPathOfTest {
          fun testMethod(path: Path) {
              // TODO: add comment for the next line
              val path1 = Path.of(path.name)
              
              // TODO: add comment for the next line
              val path2 = Path.of(path.nameWithoutExtension)
              
              // TODO: add comment for the next line
              val path3 = Path.of(path.extension)
              
              // Using Path.pathString in Path.of() should be allowed
              val path4 = Path.of(path.pathString)
              
              // Using Path.absolutePathString() in Path.of() should be allowed
              val path5 = Path.of(path.absolutePathString())
          }
      }      
      """)
  }

  fun testPathExtensionMethodsInPathResolve() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import java.nio.file.Path
      import kotlin.io.path.name
      import kotlin.io.path.nameWithoutExtension
      import kotlin.io.path.extension
      import kotlin.io.path.pathString
      import kotlin.io.path.absolutePathString
      
      class PathExtensionMethodsInPathResolveTest {
          fun testMethod(path: Path) {
              // Using Path.name in Path.resolve() should be allowed
              val path1 = path.resolve(path.name)
              
              // Using Path.nameWithoutExtension in Path.resolve() should be allowed
              val path2 = path.resolve(path.nameWithoutExtension)
              
              // Using Path.extension in Path.resolve() should be allowed
              val path3 = path.resolve(path.extension)
              
              // TO BE IMPLEMENTED: Using Path.pathString in Path.resolve() should cause a weak warning
              val path4 = path.resolve(path.pathString)
              
              // TO BE IMPLEMENTED: Using Path.absolutePathString() in Path.resolve() should cause a warning
              val path5 = path.resolve(path.absolutePathString())
          }
      }      
      """)
  }

  override fun doTest(@Language("kotlin") code: String) {
    myFixture.allowTreeAccessForAllFiles()

    super.doTest(code)
  }
}