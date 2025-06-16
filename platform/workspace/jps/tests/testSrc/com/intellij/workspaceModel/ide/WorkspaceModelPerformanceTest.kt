// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ClassLevelProjectModelExtension
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.*

class SuspendIndexingExtension : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext?) {
    System.setProperty("idea.suspend.indexes.initialization", "true")
    IndexingTestUtil.forceSkipWaiting = true
  }

  override fun afterAll(context: ExtensionContext?) {
    System.setProperty("idea.suspend.indexes.initialization", "false")
    IndexingTestUtil.forceSkipWaiting = false
  }
}

@ExtendWith(SuspendIndexingExtension::class)
@TestApplication
@RunInEdt(writeIntent = true)
class WorkspaceModelPerformanceTest {
  companion object {
    @RegisterExtension
    @JvmField
    val ourProjectModel = ClassLevelProjectModelExtension()

    private lateinit var ourProjectRoot: VirtualFileUrl
    private var disposerDebugMode = true

    @BeforeAll
    @JvmStatic
    fun initProject() {
      val workspaceModel = WorkspaceModel.getInstance(ourProjectModel.project)
      val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!.toVirtualFileUrl(
        workspaceModel.getVirtualFileUrlManager())
      ourProjectRoot = fsRoot.append(WorkspaceModelPerformanceTest::class.java.simpleName)
      val builder = MutableEntityStorage.create()
      for (i in 1..100) {
        val libRoot = ourProjectRoot.append("lib$i")
        val classesRoot = LibraryRoot(libRoot.append("classes"), LibraryRootTypeId.COMPILED)
        val sourcesRoot = LibraryRoot(libRoot.append("sources"), LibraryRootTypeId.SOURCES)
        builder.addEntity(
          LibraryEntity("lib$i", LibraryTableId.ProjectLibraryTableId, listOf(classesRoot, sourcesRoot), NonPersistentEntitySource))
      }

      for (i in 1..100) {
        val contentRoot = ourProjectRoot.append("module$i")
        val srcRoot = contentRoot.append("src")
        val dependentModules = if (i > 10) 1..10 else IntRange.EMPTY
        val dependentLibraries = (1..10).toList() + (if (i > 10) listOf(i) else emptyList())
        val dependencies = listOf(
          listOf(ModuleSourceDependency, InheritedSdkDependency),
          dependentModules.map {
            ModuleDependency(ModuleId("module$it"), false, DependencyScope.COMPILE, false)
          },
          dependentLibraries.map {
            LibraryDependency(LibraryId("lib$it", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)
          }
        ).flatten()
        builder addEntity ModuleEntity("module$i", dependencies, NonPersistentEntitySource) {
          contentRoots = listOf(ContentRootEntity(contentRoot, emptyList(), NonPersistentEntitySource) {
            sourceRoots = listOf(
              SourceRootEntity(srcRoot, JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, NonPersistentEntitySource))
          })
        }
      }

      runWriteActionAndWait {
        workspaceModel.updateProjectModel {
          it.applyChangesFrom(builder)
        }
      }

      ApplicationManagerEx.setInStressTest(true)
      disposerDebugMode = Disposer.isDebugMode()
      Disposer.setDebugMode(false)
    }

    @AfterAll
    @JvmStatic
    fun disposeProject() {
      ApplicationManagerEx.setInStressTest(false)
      Disposer.setDebugMode(disposerDebugMode)
    }
  }

  @BeforeEach
  fun setUp() {
    Assumptions.assumeTrue(UsefulTestCase.IS_UNDER_TEAMCITY, "Skip slow test on local run")
  }

  @Test
  fun `add remove module`() {
    Benchmark.newBenchmark("Adding and removing a module 10 times") {
      repeat(10) {
        val module = ourProjectModel.createModule("newModule")
        ourProjectModel.removeModule(module)
      }
    }.warmupIterations(15).start()
  }

  @Test
  fun `add remove project library`() {
    Benchmark.newBenchmark("Adding and removing a project library 30 times") {
      repeat(30) {
        val library = ourProjectModel.addProjectLevelLibrary("newLibrary") {
          it.addRoot(ourProjectRoot.append("newLibrary/classes").url, OrderRootType.CLASSES)
        }
        runWriteAction {
          ourProjectModel.projectLibraryTable.removeLibrary(library)
        }
      }
    }.warmupIterations(15).start()
  }

  @Test
  fun `add remove module library`() {
    val module = ourProjectModel.moduleManager.findModuleByName("module50")!!
    Benchmark.newBenchmark("Adding and removing a module library 10 times") {
      repeat(10) {
        val library = ourProjectModel.addModuleLevelLibrary(module, "newLibrary") {
          it.addRoot(ourProjectRoot.append("newLibrary/classes").url, OrderRootType.CLASSES)
        }
        runWriteAction {
          ModuleRootModificationUtil.removeDependency(module, library)
        }
      }
    }.warmupIterations(15).start()
  }

  @Test
  fun `add remove dependency`() {
    val module = ourProjectModel.moduleManager.findModuleByName("module50")!!
    val library = ourProjectModel.projectLibraryTable.getLibraryByName("lib40")!!
    Benchmark.newBenchmark("Adding and removing a dependency 20 times") {
      repeat(20) {
        ModuleRootModificationUtil.addDependency(module, library)
        ModuleRootModificationUtil.removeDependency(module, library)
      }
    }.warmupIterations(15).start()
  }

  @Test
  fun `process content roots`() {
    Benchmark.newBenchmark("Iterate through content roots of all modules 1000 times") {
      var count = 0
      repeat(1000) {
        ourProjectModel.moduleManager.modules.forEach { module ->
          count += ModuleRootManager.getInstance(module).contentEntries.size
        }
      }
      assertTrue(count > 0)
    }.warmupIterations(5).start()
  }

  @Test
  fun `process order entries`() {
    Benchmark.newBenchmark("Iterate through order entries of all modules 1000 times") {
      var count = 0
      repeat(1000) {
        ourProjectModel.moduleManager.modules.forEach { module ->
          count += ModuleRootManager.getInstance(module).orderEntries.size
        }
      }
      assertTrue(count > 0)
    }.warmupIterations(5).start()
  }
}
