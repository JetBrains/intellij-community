// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.util.DeleteHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Delete for the frontend Project View, end to end: deleting a file node runs the real delete handler in the
 * backend pane model, through the `DELETE_ELEMENT_PROVIDER` its own selection snapshot supplies, and the node
 * disappears from the frontend tree.
 *
 * Kept apart from [ProjectViewPaneCopyPasteTest] because this one mutates the project it runs in, and the
 * project fixture is shared by every test of a class.
 */
@TestApplication
internal class ProjectViewPaneDeleteTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPaneDeleteTest::class.java, BLUEPRINT) }

    // openAfterCreation, because deleting runs a real refactoring that expects an initialized project.
    private val project = projectFixture(
      pathFixture = tempPathFixture(subdirName = "pvDelete"),
      openAfterCreation = true,
    )
    private val module = project.moduleFixture(name = "pvDelete")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
    private val disposable = disposableFixture()
  }

  @Test
  fun `delete removes the selected file from the tree`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get()
    // Otherwise the delete handler shows its confirmation dialog and the test would hang on it.
    DeleteHandler.overrideNeedsConfirmationInTests(false, disposable.get())
    withProjectViewPane(project.get()) { pane ->
      val world = pane.expand("pvDelete", "src", "sub", "World.txt")

      pane.requestDelete(world)

      waitUntil("World.txt should have been deleted") {
        !pane.dumpSubtree("pvDelete", "src", "sub").lines().map { it.trim() }.contains("World.txt")
      }
    }
  }
}
