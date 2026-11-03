// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
class NioFileTreeModelTest {
  @TempDir
  lateinit var root: Path

  private fun descriptor(): FileChooserDescriptor = FileChooserDescriptor(true, true, false, false)

  private fun contributor(owns: (Path) -> Boolean): UniversalFileChooserContributor = object : UniversalFileChooserContributor {
    override val tabTitle: String = "Test"
    override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = emptyList()
    override fun ownsPath(path: Path): Boolean = owns(path)
  }

  private fun <T> NioFileTreeModel.onInvoker(task: () -> T): T =
    getInvoker().compute(task).blockingGet(30, TimeUnit.SECONDS)!!

  /**
   * The path filter from [FileChooserDescriptor.withPathFilter] hides the files
   * that do not match, like the extension filter. Directories stay visible.
   */
  @Test
  fun `path filter hides files that do not match and keeps directories`() {
    Files.createFile(root.resolve("match.txt"))
    Files.createFile(root.resolve("skip.log"))
    Files.createDirectories(root.resolve("dir"))
    val descriptor = descriptor()
      .withPathRoots(root)
      .withPathFilter { path -> path.fileName?.toString()?.endsWith(".txt") == true }
    val model = NioFileTreeModel(descriptor, contributor = contributor { true })
    try {
      val names = model.onInvoker {
        (0 until model.getChildCount(model.root)).map { index ->
          (model.getChild(model.root, index) as NioFileNode).path?.fileName?.toString()
        }
      }
      assertThat(names).containsExactlyInAnyOrder("match.txt", "dir")
    }
    finally {
      Disposer.dispose(model)
    }
  }
}
