// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class ProjectAndLibrariesScopeCompareTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val sdkDir: TempDirectoryExtension = TempDirectoryExtension()

  private val scope: ProjectAndLibrariesScope
    get() = ProjectAndLibrariesScope(projectModel.project)

  @Test
  fun twoModuleLibrariesOrdered() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val module = projectModel.createModule("module")
    addModuleLibrariesInOrder(module, root1, root2)

    assertTrue(scope.compare(file1, file2) > 0, "lib1 file should be preferred over lib2 file")
  }

  @Test
  fun antiSymmetry() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val module = projectModel.createModule("module")
    addModuleLibrariesInOrder(module, root1, root2)

    val forward = scope.compare(file1, file2)
    val backward = scope.compare(file2, file1)
    assertTrue(forward > 0, "Forward comparison should be positive")
    assertTrue(backward < 0, "Backward comparison should be negative")
  }

  @Test
  fun sameFileReturnsZero() {
    val module = projectModel.createModule("module")
    projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/A.java")

    assertEquals(0, scope.compare(file, file))
  }

  @Test
  fun twoFilesInSameLibraryRootReturnsZero() {
    val root = projectModel.baseProjectDir.newVirtualDirectory("root")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root/B.class")

    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.addModuleLibrary(module, "lib", listOf(root.url), emptyList())

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun differentEntryCountReturnsZero() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val file1 = projectModel.baseProjectDir.newVirtualFile("moduleA/src/A.java")
    val file2 = projectModel.baseProjectDir.newVirtualFile("moduleB/src/B.java")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA)

    // file1 has 2 entries (from moduleA and from moduleB's dependency), file2 has 1
    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun fileNotFoundInOwnerModuleReturnsZero() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    ModuleRootModificationUtil.addModuleLibrary(moduleA, "lib1", listOf(root1.url), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(moduleB, "lib2", listOf(root2.url), emptyList())

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun unrelatedModulesSourceRootsReturnsZero() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val file1 = projectModel.baseProjectDir.newVirtualFile("moduleA/src/A.java")
    val file2 = projectModel.baseProjectDir.newVirtualFile("moduleB/src/B.java")

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun consistentOrderAcrossModules() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val lib1 = projectModel.addProjectLevelLibrary("lib1") { it.addRoot(root1, OrderRootType.CLASSES) }
    val lib2 = projectModel.addProjectLevelLibrary("lib2") { it.addRoot(root2, OrderRootType.CLASSES) }

    // Both modules have same order: lib1 first, lib2 second
    ModuleRootModificationUtil.updateModel(moduleA) { model ->
      model.addLibraryEntry(lib1)
      model.addLibraryEntry(lib2)
    }
    ModuleRootModificationUtil.updateModel(moduleB) { model ->
      model.addLibraryEntry(lib1)
      model.addLibraryEntry(lib2)
    }

    assertTrue(scope.compare(file1, file2) != 0, "Should return non-zero for consistent ordering")
  }

  @Test
  fun filesOutsideAnyRootReturnsZero() {
    val file1 = projectModel.baseProjectDir.newVirtualFile("dir1/A.txt")
    val file2 = projectModel.baseProjectDir.newVirtualFile("dir2/B.txt")

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun threeLibrariesTransitivity() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val root3 = projectModel.baseProjectDir.newVirtualDirectory("root3")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")
    val file3 = projectModel.baseProjectDir.newVirtualFile("root3/C.class")

    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.updateModel(module) { model ->
      addLibraryToModel(model, "lib1", root1)
      addLibraryToModel(model, "lib2", root2)
      addLibraryToModel(model, "lib3", root3)
    }

    assertTrue(scope.compare(file1, file3) > 0, "lib1 should be preferred over lib3")
    assertTrue(scope.compare(file1, file2) > 0, "lib1 should be preferred over lib2")
    assertTrue(scope.compare(file2, file3) > 0, "lib2 should be preferred over lib3")
  }


  @Test
  fun fileInRootSharedByTwoProjectLibsSameModule() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val projLib1 = projectModel.addProjectLevelLibrary("projLib1") { it.addRoot(root1, OrderRootType.CLASSES) }
    val projLib2 = projectModel.addProjectLevelLibrary("projLib2") { it.addRoot(root1, OrderRootType.CLASSES) }
    val projLib3 = projectModel.addProjectLevelLibrary("projLib3") { it.addRoot(root2, OrderRootType.CLASSES) }

    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addLibraryEntry(projLib1)
      model.addLibraryEntry(projLib2)
      model.addLibraryEntry(projLib3)
    }

    assertEquals(1, scope.compare(file1, file2),
                 "Root1 located before root2")
  }


  @Test
  fun fileInRootSharedByTwoModuleLibsDifferentModules() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")

    ModuleRootModificationUtil.addModuleLibrary(moduleA, "mlib1", listOf(root1.url), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(moduleB, "mlib2", listOf(root1.url), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(moduleA, "mlib3", listOf(root2.url), emptyList())

    assertEquals(1, scope.compare(file1, file2),
                 "Root1 located before root2")
  }

  @Test
  fun bothFilesInRootSharedBySameTwoProjectLibs() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val projLib1 = projectModel.addProjectLevelLibrary("projLib1") {
      it.addRoot(root1, OrderRootType.CLASSES)
      it.addRoot(root2, OrderRootType.CLASSES)
    }
    val projLib2 = projectModel.addProjectLevelLibrary("projLib2") {
      it.addRoot(root1, OrderRootType.CLASSES)
      it.addRoot(root2, OrderRootType.CLASSES)
    }

    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addLibraryEntry(projLib1)
      model.addLibraryEntry(projLib2)
    }

    assertEquals(0, scope.compare(file1, file2),
                 "Same library sets → same position → 0")
  }

  @Test
  fun partialRootOverlap() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val projLib1 = projectModel.addProjectLevelLibrary("projLib1") { it.addRoot(root1, OrderRootType.CLASSES) }
    val projLib2 = projectModel.addProjectLevelLibrary("projLib2") {
      it.addRoot(root2, OrderRootType.CLASSES)
    }

    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addLibraryEntry(projLib1)
      model.addLibraryEntry(projLib2)
    }

    assertEquals(1, scope.compare(file1, file2),
                 "entry count mismatch (2 vs 1) → 0")
  }


  @Test
  fun projectAndModuleLibraryShareRoot() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val projLib = projectModel.addProjectLevelLibrary("projLib") { it.addRoot(root1, OrderRootType.CLASSES) }

    val module = projectModel.createModule("module")
    // Add module-level library with same root1
    ModuleRootModificationUtil.addModuleLibrary(module, "modLib", listOf(root1.url), emptyList())
    // Add project-level library dependency
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addLibraryEntry(projLib)
    }
    // Add another module-level library with root2
    ModuleRootModificationUtil.addModuleLibrary(module, "modLib2", listOf(root2.url), emptyList())

    assertEquals(1, scope.compare(file1, file2),
                 "Root1 located before root2")
  }


  @Test
  fun multipleLibrariesSameRootConsistentOrderAcrossModules() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    val file1 = projectModel.baseProjectDir.newVirtualFile("root1/A.class")
    val file2 = projectModel.baseProjectDir.newVirtualFile("root2/B.class")

    val projLib1 = projectModel.addProjectLevelLibrary("projLib1") { it.addRoot(root1, OrderRootType.CLASSES) }
    val projLib2 = projectModel.addProjectLevelLibrary("projLib2") { it.addRoot(root2, OrderRootType.CLASSES) }

    val moduleA = projectModel.createModule("moduleA")

    ModuleRootModificationUtil.updateModel(moduleA) { model ->
      model.addLibraryEntry(projLib2)
      model.addLibraryEntry(projLib1)
    }


    assertEquals(-1, scope.compare(file1, file2),
                 "Root2 before root1")
  }

  @Test
  fun twoFilesInSameJdkRootReturnsZero() {
    val module = projectModel.createModule("module")
    val jdkRoot = sdkDir.newVirtualDirectory("jdk")
    val file1 = sdkDir.newVirtualFile("jdk/A.class")
    val file2 = sdkDir.newVirtualFile("jdk/B.class")
    val sdk = projectModel.addSdk("jdk") { it.addRoot(jdkRoot, OrderRootType.CLASSES) }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun jdkInDifferentModulesReturnsZero() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val jdkRoot1 = sdkDir.newVirtualDirectory("jdk1")
    val jdkRoot2 = sdkDir.newVirtualDirectory("jdk2")
    val file1 = sdkDir.newVirtualFile("jdk1/A.class")
    val file2 = sdkDir.newVirtualFile("jdk2/B.class")
    val sdk1 = projectModel.addSdk("jdk1") { it.addRoot(jdkRoot1, OrderRootType.CLASSES) }
    val sdk2 = projectModel.addSdk("jdk2") { it.addRoot(jdkRoot2, OrderRootType.CLASSES) }
    ModuleRootModificationUtil.setModuleSdk(moduleA, sdk1)
    ModuleRootModificationUtil.setModuleSdk(moduleB, sdk2)

    assertEquals(0, scope.compare(file1, file2))
  }

  @Test
  fun jdkAndLibraryInDifferentModulesReturnsZero() {
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val jdkRoot = sdkDir.newVirtualDirectory("jdk")
    val jdkFile = sdkDir.newVirtualFile("jdk/A.class")
    val sdk = projectModel.addSdk("jdk") { it.addRoot(jdkRoot, OrderRootType.CLASSES) }
    ModuleRootModificationUtil.setModuleSdk(moduleA, sdk)

    val libRoot = projectModel.baseProjectDir.newVirtualDirectory("libRoot")
    val libFile = projectModel.baseProjectDir.newVirtualFile("libRoot/B.class")
    ModuleRootModificationUtil.addModuleLibrary(moduleB, "lib", listOf(libRoot.url), emptyList())

    assertEquals(0, scope.compare(jdkFile, libFile))
  }


  companion object {
    private fun addModuleLibrariesInOrder(module: Module, vararg roots: VirtualFile) {
      ModuleRootModificationUtil.updateModel(module) { model ->
        for ((i, root) in roots.withIndex()) {
          addLibraryToModel(model, "lib${i + 1}", root)
        }
      }
    }

    private fun addLibraryToModel(model: ModifiableRootModel, name: String, root: VirtualFile) {
      val lib = model.moduleLibraryTable.createLibrary(name)
      val libModel = lib.modifiableModel
      libModel.addRoot(root, OrderRootType.CLASSES)
      libModel.commit()
    }
  }
}
