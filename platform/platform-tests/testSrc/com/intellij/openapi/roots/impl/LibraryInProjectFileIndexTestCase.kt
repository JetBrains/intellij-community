// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY_SOURCE_AND_CLASSES
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generate
import com.intellij.util.io.generateInVirtualTempDir
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestApplication
@RunInEdt
abstract class LibraryInProjectFileIndexTestCase {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  protected abstract val libraryTable: LibraryTable
  protected abstract fun createLibrary(name: String = "lib", setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx

  lateinit var root: VirtualFile
  lateinit var module: Module

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    root = projectModel.baseProjectDir.newVirtualDirectory("lib")
  }

  @Test
  fun `library roots`() {
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-src")
    val docRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-doc")
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/lib-exc")
    val library = createLibrary {
      it.addRoot(root, OrderRootType.CLASSES)
      it.addRoot(srcRoot, OrderRootType.SOURCES)
      it.addRoot(docRoot, OrderRootType.DOCUMENTATION)
      it.addExcludedRoot(excludedRoot.url)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    fileIndex.assertScope(root, IN_LIBRARY)
    assertEquals(root, fileIndex.getClassRootForFile(root))
    assertNull(fileIndex.getSourceRootForFile(root))
    
    fileIndex.assertScope(srcRoot, IN_LIBRARY or IN_SOURCE)
    assertEquals(srcRoot, fileIndex.getSourceRootForFile(srcRoot))
    assertNull(fileIndex.getClassRootForFile(srcRoot))
    
    fileIndex.assertScope(docRoot, NOT_IN_PROJECT)
    assertNull(fileIndex.getClassRootForFile(docRoot))
    
    fileIndex.assertScope(excludedRoot, EXCLUDED)
    assertNull(fileIndex.getClassRootForFile(excludedRoot))
  }

  @Test
  fun `add and remove dependency on library`() {
    val library = createLibrary {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(root, NOT_IN_PROJECT)

    ModuleRootModificationUtil.addDependency(module, library)
    fileIndex.assertScope(root, IN_LIBRARY)

    ModuleRootModificationUtil.removeDependency(module, library)
    fileIndex.assertScope(root, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove library referenced from module`() {
    val name = "unresolved"
    ModuleRootModificationUtil.modifyModel(module) {
      it.addInvalidLibrary(name, libraryTable.tableLevel)
      true
    }
    fileIndex.assertScope(root, NOT_IN_PROJECT)

    val library = createLibrary(name) {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(root, IN_LIBRARY)

    runWriteActionAndWait {
      libraryTable.removeLibrary(library)
    }
    fileIndex.assertScope(root, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove root from library`() {
    val library = createLibrary()
    ModuleRootModificationUtil.addDependency(module, library)
    fileIndex.assertScope(root, NOT_IN_PROJECT)

    projectModel.modifyLibrary(library) {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(root, IN_LIBRARY)

    projectModel.modifyLibrary(library) {
      it.removeRoot(root.url, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(root, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove excluded root from library`() {
    val library = createLibrary {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/exc")
    ModuleRootModificationUtil.addDependency(module, library)
    fileIndex.assertScope(excludedRoot, IN_LIBRARY)

    projectModel.modifyLibrary(library) {
      it.addExcludedRoot(excludedRoot.url)
    }
    fileIndex.assertScope(excludedRoot, EXCLUDED)

    projectModel.modifyLibrary(library) {
      it.removeExcludedRoot(excludedRoot.url)
    }
    fileIndex.assertScope(excludedRoot, IN_LIBRARY)
  }

  @Test
  fun `add and remove JAR directory root`() {
    val library = createLibrary()
    ModuleRootModificationUtil.addDependency(module, library)
    val rootDir = directoryContent {
      zip("a.jar") { file("a.txt") }
      dir("subDir") {
        zip("b.jar") { file("b.txt") }
      }
    }.generateInVirtualTempDir()
    val jarFile = rootDir.findJarRootByRelativePath("a.jar")
    val jarInSubDir = rootDir.findJarRootByRelativePath("subDir/b.jar")
    fileIndex.assertScope(jarFile, NOT_IN_PROJECT)
    fileIndex.assertScope(jarInSubDir, NOT_IN_PROJECT)

    projectModel.modifyLibrary(library) {
      it.addJarDirectory(rootDir, false, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(jarFile, IN_LIBRARY)
    fileIndex.assertScope(jarInSubDir, NOT_IN_PROJECT)

    projectModel.modifyLibrary(library) {
      it.removeRoot(rootDir.url, OrderRootType.CLASSES)
      it.addJarDirectory(rootDir, true, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(jarFile, IN_LIBRARY)
    fileIndex.assertScope(jarInSubDir, IN_LIBRARY)

    projectModel.modifyLibrary(library) {
      it.removeRoot(rootDir.url, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(jarFile, NOT_IN_PROJECT)
    fileIndex.assertScope(jarInSubDir, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove file under JAR directory root`() {
    val recLibDir = projectModel.baseProjectDir.newVirtualDirectory("recursiveLib")

    val library = createLibrary {
      it.addJarDirectory(root, false, OrderRootType.CLASSES)
      it.addJarDirectory(recLibDir, true, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    directoryContent {
      zip("a.jar") { file("a.txt") }
      dir("subDir") {
        zip("b.jar") { file("b.txt") }
      }
    }.generate(root)
    directoryContent {
      zip("c.jar") { file("c.txt") }
      dir("subDir") {
        zip("d.jar") { file("d.txt") }
      }
    }.generate(recLibDir)
    val jarFile = root.findJarRootByRelativePath("a.jar")
    val jarInSubDir = root.findJarRootByRelativePath("subDir/b.jar")
    fileIndex.assertScope(jarInSubDir, NOT_IN_PROJECT)
    val jar1InRecSubDir = recLibDir.findJarRootByRelativePath("c.jar")
    val jar2InRecSubDir = recLibDir.findJarRootByRelativePath("subDir/d.jar")

    fileIndex.assertScope(jarFile, IN_LIBRARY)
    fileIndex.assertScope(jar1InRecSubDir, IN_LIBRARY)
    fileIndex.assertScope(jar2InRecSubDir, IN_LIBRARY)
  }

  @ParameterizedTest(name = "same library = {0}")
  @ValueSource(booleans = [false, true])
  fun `nested library roots`(sameLibrary: Boolean) {
    val innerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/inner/inner.txt")
    val outerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/outer.txt")
    val outerClassesRoot = outerFile.parent
    val innerSourceRoot = innerFile.parent
    if (sameLibrary) {
      val library = createLibrary("lib") {
        it.addRoot(outerClassesRoot, OrderRootType.CLASSES)
        it.addRoot(innerSourceRoot, OrderRootType.SOURCES)
      }
      ModuleRootModificationUtil.addDependency(module, library)
    }
    else {
      val outerLibrary = createLibrary("outer") { it.addRoot(outerClassesRoot, OrderRootType.CLASSES) }
      val innerLibrary = createLibrary("inner") { it.addRoot(innerSourceRoot, OrderRootType.SOURCES) }
      ModuleRootModificationUtil.addDependency(module, innerLibrary)
      ModuleRootModificationUtil.addDependency(module, outerLibrary)
    }
    fileIndex.assertScope(innerFile, IN_LIBRARY_SOURCE_AND_CLASSES)
    fileIndex.assertScope(outerFile, IN_LIBRARY)
    assertEquals(innerSourceRoot, fileIndex.getSourceRootForFile(innerFile))
    assertEquals(outerClassesRoot, fileIndex.getClassRootForFile(innerFile))
  }

  private fun VirtualFile.findJarRootByRelativePath(path: String): VirtualFile {
    val jarFile = findFileByRelativePath(path) ?: error("cannot find $path in ${this.presentableUrl}")
    return JarFileSystem.getInstance().getJarRootForLocalFile(jarFile) ?: error("cannot find JAR root for ${jarFile.presentableUrl}")
  }
}

