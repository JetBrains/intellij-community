// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.tests

import com.intellij.platform.projectView.pane.ProjectViewPaneStateBuilderImpl
import com.intellij.platform.projectView.pane.SUPER_ROOT_ID
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal class ProjectViewPaneStateBuilderTest {
  @Test
  fun `nodes are removed recursively`() = runTest { fixture ->
    val rootId = fixture.addNode(SUPER_ROOT_ID, "root")
    val childId = fixture.addNode(rootId, "child1")
    val grandchildId = fixture.addNode(childId, "grandchild1")
    fixture.sut.removeNodeChild(rootId, 0)
    assertThat(fixture.state.getNodeById(grandchildId)).isNull()
  }
  
  private fun runTest(test: suspend (TestFixture) -> Unit) = timeoutRunBlocking {
    val fixture = TestFixture()
    test(fixture)
  }
  
  private class TestFixture {
    val sut = ProjectViewPaneStateBuilderImpl(projectViewPaneId("TestPane"))
    val state = sut.asBackendStateAccessor<String>()
    val id = AtomicLong(0L)

    suspend fun addNode(parentId: Long, text: String): Long {
      val id = id.incrementAndFetch()
      sut.addNode(parentId, 0, buildProjectViewNodeModel(id, text) { builder ->
        builder.buildPresentation { presentationBuilder ->
          presentationBuilder.setMainText(text)
        }
      })
      return id
    }
  }
}
