// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.platform.projectView.frontend.impl.ChildrenState
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The children preloader for the frontend Project View: a request to load the children of a collapsed node goes to the backend once.
 *
 * Kept apart from [ProjectViewPaneTest], because the project fixture is shared by every test of a class.
 * The backend keeps the loaded nodes of that project, so another test can load the children before this one.
 */
@TestApplication
internal class ProjectViewPanePreloadTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPanePreloadTest::class.java, BLUEPRINT) }

    private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvPreload"))
    private val module = project.moduleFixture(name = "pvPreload")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @Test
  fun `preloading loads the children once`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    srcRoot.get()
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      val src = pane.expand("pvPreload", "src")
      assertEquals(ChildrenState.NOT_LOADED, src.childrenState, "the children of 'src' before the preload")
      pane.preloadChildren(src)
      pane.preloadChildren(src) // the children are loading now
      waitUntil("the children of 'src' should have been preloaded", PANE_TEST_WAIT_TIMEOUT) {
        pane.findLoadedNode("Hello.txt") != null
      }
      assertEquals(1, pane.loadChildrenRequestCount(src), "the load requests for the children of 'src'")
    }
  }
}
