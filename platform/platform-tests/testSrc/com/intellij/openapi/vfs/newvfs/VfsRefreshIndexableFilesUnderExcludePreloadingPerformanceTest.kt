// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.VfsRefreshIndexableFilesUnderExcludePreloadingPerformanceTest.Companion.FILES_PER_CONTENT_ROOT
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

@StressTestApplication
@PerformanceUnitTest
@RegistryKey("vfs.refresh.iterate.included.files.under.exclude", "true")
internal class VfsRefreshIndexableFilesUnderExcludePreloadingPerformanceTest {
  @RegisterExtension
  private val projectExtension = ProjectModelExtension()

  private val project: Project get() = projectExtension.project

  @Test
  fun `test refresh preload indexable files under exclude`() {
    val module = projectExtension.createModule("vfs-refresh")
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val directoryToRefresh = projectExtension.baseProjectDir.virtualFileRoot
    directoryToRefresh.children // Make the refresh below a full refresh of the already known directory.
    var layoutNumber = 0
    var layout: Layout? = null

    Benchmark.newBenchmark("VFS refresh with indexable files under an exclude") {
      VfsUtil.markDirtyAndRefresh(false, false, false, directoryToRefresh)
    }.setup {
      directoryToRefresh.refresh(false, true)
      Assertions.assertTrue((directoryToRefresh as? NewVirtualFile)?.isDirty == false)
      layout = createLayout(layoutNumber++)
      addContentRoots(module, layout)
      IndexingTestUtil.waitUntilIndexesAreReady(project) // to make sure scanning won't run in parallel with vfs refresh
      createDirectories(layout)
    }.warmupIterations(0).attempts(1).start()

    layout?.contentRoots?.forEachIndexed { index, contentRoot ->
      repeat(FILES_PER_CONTENT_ROOT) { fileIndex ->
        val file = contentRoot.resolve("File${index}_$fileIndex.kt")
        assertNotNull(VfsUtil.findFile(file, false), "Expected $file to be loaded into VFS")
      }
    }
  }

  /**
   * Creates a layout description whose content roots and excluded directory do not exist yet.
   *
   * All content roots are placed under the excluded directory and use shared or unique parent
   * prefixes. The layout also reserves a non-indexable file under the excluded directory. It looks
   * like this (each content root contains [FILES_PER_CONTENT_ROOT] Kotlin files):
   *
   * ```
   * refresh-root-N/
   * └── excluded/
   *     ├── common-prefix/
   *     │   ├── content-root-0/
   *     │   ├── content-root-4/
   *     │   ├── ...
   *     │   └── nested-prefix/
   *     │       └── nested-prefix-0/
   *     │           └── nested-prefix-1/
   *     │               └── ...         (nested-prefix-0 through nested-prefix-9)
   *     │                   ├── content-root-1/
   *     │                   │   ├── File1_0.kt
   *     │                   │   └── ...
   *     │                   ├── content-root-2/
   *     │                   └── ...
   *     ├── unique-prefix-3/
   *     │   └── content-root-3/
   *     ├── unique-prefix-7/
   *     │   └── content-root-7/
   *     ├── ...
   *     └── not-indexable/
   *         └── ignored.txt
   * ```
   *
   * The paths are materialized on disk later by [createDirectories].
   */
  private fun createLayout(number: Int): Layout {
    val directory = projectExtension.baseProjectDir.rootPath.resolve("refresh-root-$number")
    val excluded = directory.resolve("excluded")

    val contentRoots = buildList {
      repeat(CONTENT_ROOT_COUNT) { index ->
        val prefix = when (index % 4) {
          0 -> excluded.resolve("common-prefix")
          1, 2 -> (0 until NESTED_PREFIX_DEPTH).fold(excluded.resolve("common-prefix")) { path, depth ->
            path.resolve("nested-prefix-$depth")
          }
          else -> excluded.resolve("unique-prefix-$index")
        }
        add(prefix.resolve("content-root-$index"))
      }
    }

    return Layout(directory, excluded, contentRoots)
  }

  private fun createDirectories(layout: Layout) {
    layout.directory.createDirectories()
    layout.excluded.createDirectories()
    layout.contentRoots.forEachIndexed { index, contentRoot ->
      contentRoot.createDirectories()
      repeat(FILES_PER_CONTENT_ROOT) { fileIndex ->
        contentRoot.resolve("File${index}_$fileIndex.kt").createFile()
      }
    }
    layout.excluded.resolve("not-indexable").resolve("ignored.txt").apply {
      parent.createDirectories()
      createFile()
    }
  }

  private fun addContentRoots(module: Module, layout: Layout) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntry = model.addContentEntry(layout.directory.toUrl())
      contentEntry.addExcludeFolder(layout.excluded.toUrl())
      layout.contentRoots.forEach { contentRoot ->
        model.addContentEntry(contentRoot.toUrl())
      }
    }
  }

  private fun Path.toUrl(): String = VfsUtilCore.pathToUrl(toString())

  private data class Layout(
    val directory: Path,
    val excluded: Path,
    val contentRoots: List<Path>,
  )

  companion object {
    private const val CONTENT_ROOT_COUNT = 100
    private const val FILES_PER_CONTENT_ROOT = 5_000
    private const val NESTED_PREFIX_DEPTH = 10
  }
}
