// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.path

import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import kotlin.concurrent.fixedRateTimer

/**
 * Tests for PathAnnotationInspection with Kotlin extension methods from kotlin.io.path.
 *
 * According to the requirements:
 * - `Path.name`, `Path.nameWithoutExtension`, `Path.extension` should be considered as returning `@Filename`
 * - `Path.pathString`, `Path.absolutePathString()` should be considered as returning `@MultiRoutingFileSystemPath`
 */
class PathAnnotationInspectionKotlinExtensionMethodsTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()



  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun testPathNameExtensionMethod() {
    myFixture.allowTreeAccessForAllFiles()

    doTest("""
      @file:Suppress("UNUSED_VARIABLE")
      
      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import java.nio.file.Path
      import kotlin.io.path.name
      
      class PathNameExtensionMethodTest {
          fun testMethod() {
              val path = Path.of(<warning descr="${message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")}">"/some/path"</warning>)

              // TODO: add comment for the next line
              val filenamePath = Path.of(path.name)
              
              // Path.name should be considered as returning @Filename
              val name = path.name

              // TODO: add comment for the next line
              val filenamePathFromVariable = Path.of(name)

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
          fun testMethod() {
              val path = Path.of("/some/path/file.txt")
              
              // Path.nameWithoutExtension should be considered as returning @Filename
              val nameWithoutExtension = path.nameWithoutExtension
              
              // Using nameWithoutExtension in a context that expects @Filename should be allowed
              @Filename val filename: String = nameWithoutExtension // No warning
              
              // Using nameWithoutExtension in a context that expects @MultiRoutingFileSystemPath should cause a warning
              @MultiRoutingFileSystemPath val multiRoutingPath: String = <warning descr="${
      message("inspections.message.string.annotated.with.passed.to.method.parameter.annotated.with", "@Filename", "@MultiRoutingFileSystemPath")
    }">nameWithoutExtension</warning>
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
          fun testMethod() {
              val path = Path.of("/some/path/file.txt")
              
              // Path.extension should be considered as returning @Filename
              val extension = path.extension
              
              // Using extension in a context that expects @Filename should be allowed
              @Filename val filename: String = extension // No warning
              
              // Using extension in a context that expects @MultiRoutingFileSystemPath should cause a warning
              @MultiRoutingFileSystemPath val multiRoutingPath: String = <warning descr="${
      message("inspections.message.string.annotated.with.passed.to.method.parameter.annotated.with", "@Filename", "@MultiRoutingFileSystemPath")
    }">extension</warning>
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
          fun testMethod() {
              val path = Path.of("/some/path")
              
              // Path.pathString should be considered as returning @MultiRoutingFileSystemPath
              val pathString = path.pathString
              
              // Using pathString in a context that expects @MultiRoutingFileSystemPath should be allowed
              @MultiRoutingFileSystemPath val multiRoutingPath: String = pathString // No warning
              
              // Using pathString in a context that expects @Filename should cause a warning
              @Filename val filename: String = <warning descr="${
      message("inspections.message.string.annotated.with.passed.to.method.parameter.annotated.with", "@MultiRoutingFileSystemPath", "@Filename")
    }">pathString</warning>
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
          fun testMethod() {
              val path = Path.of("/some/path")
              
              // Path.absolutePathString() should be considered as returning @MultiRoutingFileSystemPath
              val absolutePathString = path.absolutePathString()
              
              // Using absolutePathString in a context that expects @MultiRoutingFileSystemPath should be allowed
              @MultiRoutingFileSystemPath val multiRoutingPath: String = absolutePathString // No warning
              
              // Using absolutePathString in a context that expects @Filename should cause a warning
              @Filename val filename: String = <warning descr="${
      message("inspections.message.string.annotated.with.passed.to.method.parameter.annotated.with", "@MultiRoutingFileSystemPath", "@Filename")
    }">absolutePathString</warning>
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
          fun testMethod() {
              val path = Path.of("/some/path/file.txt")
              
              // Using Path.name in Path.of() should cause a warning
              val path1 = Path.of(<warning descr="${
      message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">path.name</warning>)
              
              // Using Path.nameWithoutExtension in Path.of() should cause a warning
              val path2 = Path.of(<warning descr="${
      message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">path.nameWithoutExtension</warning>)
              
              // Using Path.extension in Path.of() should cause a warning
              val path3 = Path.of(<warning descr="${
      message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">path.extension</warning>)
              
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
          fun testMethod() {
              val path = Path.of("/some/path/file.txt")
              
              // Using Path.name in Path.resolve() should be allowed
              val path1 = path.resolve(path.name)
              
              // Using Path.nameWithoutExtension in Path.resolve() should be allowed
              val path2 = path.resolve(path.nameWithoutExtension)
              
              // Using Path.extension in Path.resolve() should be allowed
              val path3 = path.resolve(path.extension)
              
              // Using Path.pathString in Path.resolve() should cause a warning
              val path4 = path.resolve(<warning descr="${
      message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")
    }">path.pathString</warning>)
              
              // Using Path.absolutePathString() in Path.resolve() should cause a warning
              val path5 = path.resolve(<warning descr="${
      message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")
    }">path.absolutePathString()</warning>)
          }
      }      
      """)
  }
}