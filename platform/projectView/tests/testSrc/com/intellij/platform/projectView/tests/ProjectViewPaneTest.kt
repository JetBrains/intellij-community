// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Example almost-end-to-end test for the frontend Project View.
 *
 * A real project (a module with one source root seeded from `testData/paneTreeExample`) is fed through
 * the real backend pipeline and the real `FrontendProjectViewPaneAggregator` — across the in-process
 * DTO round-trip (`event.toDTO()` on the backend, `dto.toEvent()` in the aggregator; no serialization)
 * — into a real `FrontendProjectViewPaneTreeModel`, and the resulting tree is asserted. The Swing pane
 * (`TreeBasedFrontendProjectViewPane`) is deliberately not involved.
 *
 * To add more tests, extend [AbstractProjectViewPaneTest], declare the fixtures you need, and drive a
 * pane via [withProjectViewPane] / [ProjectViewPaneTester].
 */
@TestApplication
internal class ProjectViewPaneTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPaneTest::class.java, BLUEPRINT) }

    // Pin the project and source-root directory names so the rendered tree is deterministic.
    private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvExample"))
    private val module = project.moduleFixture(name = "pvExample")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @Test
  fun `project pane tree and subtree assert tests`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get() // materialize the project + module + source root before opening the pane
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(
        """
        pvExample
         src
          sub
           World.txt
          Hello.txt
         External Libraries
        """
      )
      pane.assertSubtree(
        listOf("pvExample", "src"),
        """
        src
         sub
          World.txt
         Hello.txt
        """
      )
    }
  }
}
