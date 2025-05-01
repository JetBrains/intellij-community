// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.path

import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionTestBase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Kotlin implementation of tests for PathAnnotationInspection.
 * Some tests might not pass because the inspection implementation might not fully support Kotlin.
 */
class PathAnnotationInspectionKotlinTest : PathAnnotationInspectionTestBase() {
  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

  override fun setUp() {
    super.setUp()
    //setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun testFileSystemGetPath() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.Filename
      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.FileSystems
      import java.nio.file.FileSystem

      class FileSystemGetPath {
          fun testMethod() {
              val fs: FileSystem = FileSystems.getDefault()

              // First argument should be annotated with @NativePath
              val nonAnnotatedPath = "/usr/local/bin"
              fs.getPath(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")
    }">nonAnnotatedPath</warning>, "file.txt")

              // First argument with @NativePath is correct
              @NativePath val nativePath = "/usr/local/bin"
              fs.getPath(nativePath, "file.txt")

              // Elements of 'more' parameter should be annotated with either @NativePath or @Filename
              val nonAnnotatedMore = "invalid/path"
              fs.getPath(nativePath, <warning descr="${
      DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")
    }">nonAnnotatedMore</warning>)

              // Elements of 'more' parameter with @NativePath is correct
              @NativePath val nativeMore = "subdir"
              fs.getPath(nativePath, nativeMore)

              // Elements of 'more' parameter with @Filename is also correct
              @Filename val filenameMore = "file.txt"
              fs.getPath(nativePath, filenameMore)
          }
      }      
      """)
  }

  fun testNativePathInPathOf() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.Path

      class NativePathInPathOf {
          fun testMethod() {
              @NativePath val nativePath = "/usr/local/bin"
              // This should be highlighted as an error because @NativePath strings should not be used directly in Path.of()
              val path = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.nativepath.should.not.be.used.directly.constructing.path")
    }">nativePath</warning>)
          }
      }      
      """)
  }

  fun testKotlinPathResolve() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import com.intellij.platform.eel.annotations.Filename
      import java.nio.file.Path
      import java.nio.file.Paths

      class PathResolve {
          fun testMethod() {
              // Create a base path
              @MultiRoutingFileSystemPath val basePath = "/base/path"
              val base = Path.of(basePath)

              // Test with string literals
              val path1 = base.resolve("file.txt") // No warning, "file.txt" is a valid filename
              val path2 = base.resolve(<weak_warning descr="${
      DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")
    }">"invalid/filename"</weak_warning>) // Warning, contains slash

              // Test with annotated strings
              @MultiRoutingFileSystemPath val multiRoutingPath = "some/path"
              @Filename val filename = "file.txt"

              val path3 = base.resolve(multiRoutingPath) // No warning, annotated with @MultiRoutingFileSystemPath
              val path4 = base.resolve(filename) // No warning, annotated with @Filename

              // Test with string constants
              val validFilename = "file.txt"
              val invalidFilename = "invalid/filename"

              val path5 = base.resolve(validFilename) // No warning, validFilename is a valid filename
              val path6 = base.resolve(<weak_warning descr="${
      DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")
    }">invalidFilename</weak_warning>) // Warning, contains slash
          }
      }      
      """)
  }

  fun testKotlinStringLiteralFilename() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.FileSystem
      import java.nio.file.FileSystems
      import java.nio.file.Path

      class StringLiteralFilename {
          fun testMethod() {
              // String literals that denote a filename (without slashes) should be allowed in Path.of() more parameters
              @MultiRoutingFileSystemPath val basePath = "/base/path"
              val path1 = Path.of(basePath, "file.txt") // No warning, "file.txt" is a valid filename
              val path2 = Path.of(basePath, <warning descr="${
      DevKitBundle.message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")
    }">"invalid/filename"</warning>) // Warning, contains slash

              // String literals that denote a filename should be allowed in FileSystem.getPath() more parameters
              val fs: FileSystem = FileSystems.getDefault()
              @NativePath val nativePath = "/usr/local/bin"
              fs.getPath(nativePath, "file.txt") // No warning, "file.txt" is a valid filename
              fs.getPath(nativePath, <warning descr="${
      DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")
    }">"invalid/filename"</warning>) // Warning, contains slash

              // String constants that denote a filename should also be allowed
              val validFilename = "file.txt"
              val invalidFilename = "invalid/filename"

              val path3 = Path.of(basePath, validFilename) // No warning, validFilename is a valid filename
              val path4 = Path.of(basePath, <warning descr="${
      DevKitBundle.message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename")
    }">invalidFilename</warning>) // Warning, contains slash

              fs.getPath(nativePath, validFilename) // No warning, validFilename is a valid filename
              fs.getPath(nativePath, <warning descr="${
      DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename")
    }">invalidFilename</warning>) // Warning, contains slash
          }
      }      
      """)
  }

  fun testKotlinLocalPath() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import com.intellij.platform.eel.annotations.LocalPath
      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.Path
      import java.nio.file.FileSystem
      import java.nio.file.FileSystems

      class LocalPathTest {
          fun testMethod() {
              // Test @LocalPath in Path.of()
              @LocalPath val localPath = "/local/path"
              val path1 = Path.of(localPath) // @LocalPath can be used directly in Path.of()

              // Test @LocalPath in Path.resolve()
              @MultiRoutingFileSystemPath val basePath = "/base/path"
              val base = Path.of(basePath)
              val path2 = base.resolve(<warning descr="${
      DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method")
    }">localPath</warning>) // Warning, @LocalPath should not be used in Path.resolve()

              // Test @LocalPath in FileSystem.getPath()
              val fs = FileSystems.getDefault()
              fs.getPath(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")
    }">localPath</warning>, "file.txt") // Warning, first argument should be @NativePath
          }
      }      
      """)
  }

  fun testPathToString() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.Path
      import java.nio.file.FileSystems

      class PathToStringTest {
          fun testMethod() {
              // Test Path.toString() used in Path.of()
              val basePath = Path.of("somepath") // no warning
              val childPath = Path.of(basePath.toString(), "child") // TODO: add comment

              // Test storing Path.toString() in a variable and then using it
              val basePathString = basePath.toString()
              val anotherChildPath = Path.of(basePathString, "anotherChild") // TODO: add comment

              // Test with annotated path
              @MultiRoutingFileSystemPath val annotatedPath = "/some/path"
              val path = Path.of(annotatedPath)
              val pathString = path.toString()
              val newPath = Path.of(pathString, "subdir") // TODO: add comment

              // Test with multiple toString() calls
              val nestedPath = Path.of(Path.of("root").toString(), Path.of("nested").toString()) // TODO: add comment
          }
      }      
      """)
  }

  fun testPathToStringAdditionalCases() {
    doTest("""
      @file:Suppress("UNUSED_VARIABLE")

      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
      import com.intellij.platform.eel.annotations.NativePath
      import java.nio.file.Path
      import java.nio.file.FileSystems

      class PathToStringAdditionalCasesTest {
          fun testMethod() {
              // Test Path.toString() with Path.resolve()
              val basePath = Path.of("base")
              val resolvedPath = basePath.resolve(Path.of("child").toString()) // TODO: add comment

              // Test Path.toString() with FileSystem.getPath()
              val fs = FileSystems.getDefault()
              @NativePath val nativePath = "/usr/local/bin"
              val fsPath = fs.getPath(nativePath, Path.of("file.txt").toString()) // TODO: add comment

              // Test Path.toString() with string concatenation
              val concatPath = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">basePath.toString() + "/concat"</warning>) // warning, string concatenation with Path.toString() is not recognized as special

              // Test Path.toString() with string templates
              val templatePath = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">"${'$'}{basePath.toString()}/template"</warning>) // warning, string template with Path.toString() is not recognized as special

              // Test Path.toString() with substring operations
              val subPath = basePath.toString().substring(0, 2)
              val subPathResult = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">subPath</warning>, "sub") // warning, substring of Path.toString() is not recognized as special

              // Test Path.toString() with replace operations
              val replacedPath = basePath.toString().replace("base", "replaced")
              val replacedPathResult = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">replacedPath</warning>) // warning, replaced Path.toString() is not recognized as special

              // Test Path.toString() with trim operations
              val trimmedPath = basePath.toString().trim()
              val trimmedPathResult = Path.of(<warning descr="${
      DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
    }">trimmedPath</warning>) // warning, trimmed Path.toString() is not recognized as special
          }
      }      
      """)
  }
}
