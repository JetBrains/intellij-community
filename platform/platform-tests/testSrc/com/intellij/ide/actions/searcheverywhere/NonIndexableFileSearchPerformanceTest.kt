// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.ide.registerProjectRootBlocking
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.Path
import kotlin.reflect.jvm.javaMethod

/**
 * Performance test for [NonIndexableFilesSEContributor].
 */
@StressTestApplication
@RegistryKey(key = "se.enable.non.indexable.files.contributor", value = "true")
@PerformanceUnitTest
open class NonIndexableFileSearchPerformanceTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project get() = projectModel.project
  private val workspaceFileIndex get() = WorkspaceFileIndex.getInstance(project)


  companion object {
    private val communityPath = Path(PathManager.getCommunityHomePath())
    private val communityVirtualFile = VfsUtil.findFile(communityPath, true)!!

    private val nonIndexableFilesCount: Int = run {
      var nonIndexableFiles = 0
      VfsUtil.processFilesRecursively(NewVirtualFile.asCacheAvoiding(communityVirtualFile)) {
        nonIndexableFiles++
        true
      }
      nonIndexableFiles
    }
  }

  @BeforeEach
  fun createNonIndexableFileset(): Unit = runBlocking {
    Assumptions.assumeTrue(project.isOpen)

    runInEdtAndWait { registerProjectRootBlocking(project, communityPath) }

    Assumptions.assumeTrue(readAction { workspaceFileIndex.isInContent(communityVirtualFile) }) {
      "project root must be in content"
    }
    Assumptions.assumeFalse(readAction { workspaceFileIndex.isIndexable(communityVirtualFile) }) {
      "project root must be non-indexable"
    }
  }

  fun `iterate over all files - base`(subtestName: String) {
    val searchPattern = "ProjectRootEntity"
    val contributor = createContributor()
    Benchmark.newBenchmarkWithVariableInputSize("search \"$searchPattern\"", nonIndexableFilesCount) {
      contributor.search(searchPattern, MockProgressIndicator())
      nonIndexableFilesCount
    }.start(NonIndexableFileSearchPerformanceTest::`iterate over all files`.javaMethod!!, subtestName)
  }

  @Test
  fun `iterate over all files`() {
    `iterate over all files - base`("dfs - blocking read actions")
  }

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "true")
  fun `iterate over all files - bfs many read actions`() = `iterate over all files - base`("bfs - blocking read actions")

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "false")
  fun `iterate over all files - bfs one read action`() = `iterate over all files - base`("bfs - one read action")

  fun `search for one file deep inside - base`(subtestName: String) {
    val searchPattern = "ProjectRootEntity"
    val contributor = createContributor()
    Benchmark.newBenchmarkWithVariableInputSize("search \"$searchPattern\"", nonIndexableFilesCount) {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(searchPattern, MockProgressIndicator(), elementsLimit)
      nonIndexableFilesCount
    }.start(NonIndexableFileSearchPerformanceTest::`search for one file deep inside`.javaMethod!!, subtestName)
  }

  @Test
  fun `search for one file deep inside`() {
    `search for one file deep inside - base`("dfs - blocking read actions")
  }

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "true")
  fun `search for one file deep inside - bfs many read actions`() = `search for one file deep inside - base`("bfs - blocking read actions")

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "false")
  fun `search for one file deep inside - bfs one read action`() = `search for one file deep inside - base`("bfs - one read action")

  fun `search for one last root child - base`(subtestName: String) {
    val filename = communityVirtualFile.getChildren(true).last().name
    val contributor = createContributor()
    Benchmark.newBenchmarkWithVariableInputSize("search \"$filename\"", nonIndexableFilesCount) {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(filename, MockProgressIndicator(), elementsLimit)
      nonIndexableFilesCount
    }.start(NonIndexableFileSearchPerformanceTest::`search for one last root child`.javaMethod!!, subtestName)
  }

  @Test
  fun `search for one last root child`() {
    `search for one last root child - base`("dfs - blocking read actions")
  }

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "true")
  fun `search for one last root child - bfs many read actions`() = `search for one last root child - base`("bfs - blocking read actions")

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "false")
  fun `search for one last root child - bfs one read action`() = `search for one last root child - base`("bfs - one read action")

  fun `search for the first root child - base`(subtestName: String) {
    val filename = communityVirtualFile.getChildren(true).first().name
    val contributor = createContributor()
    Benchmark.newBenchmark("search \"$filename\"") {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(filename, MockProgressIndicator(), elementsLimit)
    }.start(NonIndexableFileSearchPerformanceTest::`search for the first root child`.javaMethod!!, subtestName)
  }

  @Test
  fun `search for the first root child`() {
    `search for the first root child - base`("dfs - blocking read actions")
  }

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "true")
  fun `search for the first root child - bfs many read actions`() = `search for the first root child - base`("bfs - blocking read actions")

  @Test
  @RegistryKey("se.enable.non.indexable.files.use.bfs", "true")
  @RegistryKey("se.enable.non.indexable.files.use.bfs.blocking.read.actions", "false")
  fun `search for the first root child - bfs one read action`() = `search for the first root child - base`("bfs - one read action")

  private fun createContributor(): NonIndexableFilesSEContributor {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    return NonIndexableFilesSEContributor(event).also { contributor ->
      Disposer.register(projectModel.disposableRule.disposable, contributor)
    }
  }
}
