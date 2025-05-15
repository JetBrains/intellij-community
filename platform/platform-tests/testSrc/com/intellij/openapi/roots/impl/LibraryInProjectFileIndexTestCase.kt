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
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generate
import com.intellij.util.io.generateInVirtualTempDir
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestApplication
@RunInEdt(writeIntent = true)
abstract class LibraryInProjectFileIndexTestCase {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @RegisterExtension
  @JvmField
  val baseLibraryDir: TempDirectoryExtension = TempDirectoryExtension()

  protected abstract val worksViaWorkspaceModel: Boolean
  protected abstract val libraryTable: LibraryTable?
  protected abstract fun createLibrary(name: String = "lib", setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx
  
  protected open fun addDependency(library: LibraryEx) {
    ModuleRootModificationUtil.addDependency(module, library)
  }

  lateinit var root: VirtualFile
  lateinit var module: Module

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    root = baseLibraryDir.newVirtualDirectory("lib")
  }

  @Test
  fun `library roots`() {
    val srcRoot = baseLibraryDir.newVirtualDirectory("lib-src")
    val docRoot = baseLibraryDir.newVirtualDirectory("lib-doc")
    val excludedRoot = baseLibraryDir.newVirtualDirectory("lib/lib-exc")
    val library = createLibrary {
      it.addRoot(root, OrderRootType.CLASSES)
      it.addRoot(srcRoot, OrderRootType.SOURCES)
      it.addRoot(docRoot, OrderRootType.DOCUMENTATION)
      it.addExcludedRoot(excludedRoot.url)
    }
    addDependency(library)

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
    if (worksViaWorkspaceModel) {
      assertEquals(library.name, fileIndex.findContainingLibraries(root).single().name)
      assertEquals(library.name, fileIndex.findContainingLibraries(srcRoot).single().name)
      assertEquals(0, fileIndex.findContainingLibraries(docRoot).size)
      assertEquals(0, fileIndex.findContainingLibraries(excludedRoot).size)
    }
  }

  @Test
  fun `add and remove dependency on library`() {
    Assumptions.assumeTrue(libraryTable != null, "Doesn't make sense for module-level libraries")
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
    Assumptions.assumeTrue(libraryTable != null, "Doesn't make sense for module-level libraries")
    val libraryTable = libraryTable!!
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
    addDependency(library)
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
    val excludedRoot = baseLibraryDir.newVirtualDirectory("lib/exc")
    addDependency(library)
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
    addDependency(library)
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
    addDependency(library)

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
    val innerFile = baseLibraryDir.newVirtualDirectory("outer/inner/inner.txt")
    val outerFile = baseLibraryDir.newVirtualDirectory("outer/outer.txt")
    val outerClassesRoot = outerFile.parent
    val innerSourceRoot = innerFile.parent
    if (sameLibrary) {
      val library = createLibrary("lib") {
        it.addRoot(outerClassesRoot, OrderRootType.CLASSES)
        it.addRoot(innerSourceRoot, OrderRootType.SOURCES)
      }
      addDependency(library)
    }
    else {
      val outerLibrary = createLibrary("outer") { it.addRoot(outerClassesRoot, OrderRootType.CLASSES) }
      val innerLibrary = createLibrary("inner") { it.addRoot(innerSourceRoot, OrderRootType.SOURCES) }
      addDependency(innerLibrary)
      addDependency(outerLibrary)
      if (worksViaWorkspaceModel) {
        assertEquals("inner", fileIndex.findContainingLibraries(innerSourceRoot).single().name)
        assertEquals("outer", fileIndex.findContainingLibraries(outerClassesRoot).single().name)
      }
    }
    fileIndex.assertScope(innerFile, IN_LIBRARY_SOURCE_AND_CLASSES)
    fileIndex.assertScope(outerFile, IN_LIBRARY)
    assertEquals(innerSourceRoot, fileIndex.getSourceRootForFile(innerFile))
    assertEquals(outerClassesRoot, fileIndex.getClassRootForFile(innerFile))
  }
  
  @Test
  fun `same root in two libraries`() {
    val library1 = createLibrary("lib1") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    val library2 = createLibrary("lib2") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    addDependency(library1)
    addDependency(library2)
    fileIndex.assertScope(root, IN_LIBRARY)
    if (worksViaWorkspaceModel) {
      assertEquals(setOf("lib1", "lib2"), fileIndex.findContainingLibraries(root).mapTo(HashSet()) { it.name })
    }
  }

  private fun VirtualFile.findJarRootByRelativePath(path: String): VirtualFile {
    val jarFile = findFileByRelativePath(path) ?: error("cannot find $path in ${this.presentableUrl}")
    return JarFileSystem.getInstance().getJarRootForLocalFile(jarFile) ?: error("cannot find JAR root for ${jarFile.presentableUrl}")
  }
}

