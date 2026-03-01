// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.ide.metrics.benchmark.Benchmark.newBenchmark
import com.intellij.tools.ide.metrics.benchmark.Benchmark.newBenchmarkWithVariableInputSize
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.ide.registerProjectRootBlocking
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.Path

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

  @Test
  fun `iterate over all files`() {
    val searchPattern = "ProjectRootEntity"
    val contributor = createContributor()
    newBenchmarkWithVariableInputSize("search \"$searchPattern\"", nonIndexableFilesCount) {
      contributor.search(searchPattern, MockProgressIndicator())
      nonIndexableFilesCount
    }.start()
  }

  @Test
  fun `search for one file deep inside`() {
    val searchPattern = "ProjectRootEntity"
    val contributor = createContributor()
    newBenchmarkWithVariableInputSize("search \"$searchPattern\"", nonIndexableFilesCount) {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(searchPattern, MockProgressIndicator(), elementsLimit)
      nonIndexableFilesCount
    }.start()
  }

  @Test
  fun `search for one last root child`() {
    val filename = communityVirtualFile.getChildren(true)!!.last().name
    val contributor = createContributor()
    newBenchmarkWithVariableInputSize("search \"$filename\"", nonIndexableFilesCount) {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(filename, MockProgressIndicator(), elementsLimit)
      nonIndexableFilesCount
    }.start()
  }

  @Test
  fun `search for the first root child`() {
    val filename = communityVirtualFile.getChildren(true)!!.first().name
    val contributor = createContributor()
    newBenchmark("search \"$filename\"") {
      // elementsLimit = 0, so when the first matching file is found, the search stops.
      // Because it actually searches for `elementsLimit + 1` files
      val elementsLimit = 0
      contributor.search(filename, MockProgressIndicator(), elementsLimit)
    }.start()
  }


  private fun createContributor(): NonIndexableFilesSEContributor {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    return NonIndexableFilesSEContributor(event).also { contributor ->
      val scope = runReadAction { GlobalSearchScope.projectScope(project) }
      contributor.setScope(ScopeDescriptor(scope))
      Disposer.register(projectModel.disposableRule.disposable, contributor)
    }
  }
}
