// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.storeAndGet
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.openapi.util.Disposer
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ClassLevelProjectModelExtension
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Measures [ProjectFileIndex.iterateContent] on a project with many content roots and one large content root.
 *
 * The many roots are one-file directories on a local disk, all in one entity. The large root is in `temp://`.
 * The roots step and the walk of the files are measured apart.
 */
@StressTestApplication
@TestApplication
@PerformanceUnitTest
class ProjectFileIndexIterateContentPerformanceTest {
  companion object {
    @RegisterExtension
    @JvmField
    val ourProjectModel = ClassLevelProjectModelExtension()

    /** The local directory of the one-file roots. The roots are on a disk, not in the `temp://` file system. */
    @TempDir
    @JvmField
    var ourRootsDir: Path? = null

    private lateinit var ourProjectRoot: VirtualFile
    private val ourContributorDisposable = Disposer.newDisposable()

    @BeforeAll
    @JvmStatic
    fun initProject() {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), ourContributorDisposable)

      val rootsBase = requireNotNull(ourRootsDir).resolve("roots")
      repeat(ONE_FILE_ROOTS) { i ->
        rootsBase.resolve("r$i").createDirectories().resolve("a.txt").writeText("a")
      }
      val vRootsBase = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(rootsBase))

      runWriteActionAndWait {
        val builder = MutableEntityStorage.create()
        val fileUrlManager = WorkspaceModel.getInstance(ourProjectModel.project).getVirtualFileUrlManager()
        val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
        ourProjectRoot = fsRoot.subdir(ProjectFileIndexIterateContentPerformanceTest::class.java.simpleName)

        val bigModuleRoot = ourProjectRoot.subdir("big")
        repeat(BIG_MODULE_DIRECTORIES) { dir ->
          val directory = bigModuleRoot.subdir("dir$dir")
          repeat(BIG_MODULE_FILES_PER_DIRECTORY) { file -> directory.file("File$file.txt") }
        }
        builder addEntity ModuleEntity("big", listOf(InheritedSdkDependency, ModuleSourceDependency), NonPersistentEntitySource) {
          contentRoots = listOf(ContentRootEntity(fileUrlManager.storeAndGet(bigModuleRoot), emptyList(), NonPersistentEntitySource))
        }

        val roots = List(ONE_FILE_ROOTS) { i -> fileUrlManager.storeAndGet(requireNotNull(vRootsBase.findChild("r$i"))) }
        builder addEntity IndexingTestEntity(roots, emptyList(), NonPersistentEntitySource)

        WorkspaceModel.getInstance(ourProjectModel.project).updateProjectModel("set up test") {
          it.applyChangesFrom(builder)
        }
      }
    }

    private fun VirtualFile.subdir(name: String): VirtualFile = createChildDirectory(ourProjectModel, name)
    private fun VirtualFile.file(name: String): VirtualFile = createChildData(ourProjectModel, name)

    @AfterAll
    @JvmStatic
    fun disposeProject() {
      VfsTestUtil.deleteFile(ourProjectRoot)
      Disposer.dispose(ourContributorDisposable)
    }

    private val fileIndex: ProjectFileIndex
      get() = ProjectFileIndex.getInstance(ourProjectModel.project)
  }

  /** The processor stops at the first file, so the time is the roots step of the walk. */
  @Test
  fun `roots step with many content roots`() {
    Benchmark.newBenchmark("iterateContent roots step with many content roots") {
      repeat(ROOTS_STEP_CALLS_PER_ATTEMPT) {
        var visited = 0
        fileIndex.iterateContent(ContentIterator {
          visited++
          false
        })
        assertEquals(1, visited)
      }
    }.attempts(ATTEMPTS).warmupIterations(WARMUP_ITERATIONS).start()
  }

  @Test
  fun `walk without a filter`() {
    Benchmark.newBenchmark("iterateContent walk without a filter") {
      repeat(WALKS_PER_ATTEMPT) {
        assertEquals(ALL_FILES, countContentFiles(filter = null))
      }
    }.attempts(ATTEMPTS).warmupIterations(WARMUP_ITERATIONS).start()
  }

  /** The reference for the walk without a filter. The platform runs a custom filter in a read action for each file. */
  @Test
  fun `walk with a filter`() {
    Benchmark.newBenchmark("iterateContent walk with a filter") {
      repeat(WALKS_PER_ATTEMPT) {
        assertEquals(ALL_FILES, countContentFiles(filter = VirtualFileFilter { true }))
      }
    }.attempts(ATTEMPTS).warmupIterations(WARMUP_ITERATIONS).start()
  }

  private fun countContentFiles(filter: VirtualFileFilter?): Int {
    var files = 0
    fileIndex.iterateContent(ContentIterator { file ->
      if (!file.isDirectory) files++
      true
    }, filter)
    return files
  }
}

private const val BIG_MODULE_DIRECTORIES = 100
private const val BIG_MODULE_FILES_PER_DIRECTORY = 1000
private const val ONE_FILE_ROOTS = 20_000
private const val ALL_FILES = BIG_MODULE_DIRECTORIES * BIG_MODULE_FILES_PER_DIRECTORY + ONE_FILE_ROOTS

private const val ROOTS_STEP_CALLS_PER_ATTEMPT = 100
private const val WALKS_PER_ATTEMPT = 10
private const val ATTEMPTS = 10
private const val WARMUP_ITERATIONS = 3
