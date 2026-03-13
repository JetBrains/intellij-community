// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.service.AgentWorkbenchTerminalHyperlinkNavigationInterceptor
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest {
  @Test
  fun dedicatedFrameFileHyperlinkUsesSourceProjectRouting() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val sourceDescriptor = OpenFileDescriptor(currentProject, file, 4, 2)
      val hyperlinkInfo = TestFileHyperlinkInfo(sourceDescriptor)

      var openCalls = 0
      var focusCalls = 0
      var navigatedDescriptor: OpenFileDescriptor? = null
      val interceptor = AgentWorkbenchTerminalHyperlinkNavigationInterceptor(
        selectedSourcePath = { "/tmp/source-project" },
        isDedicatedProject = { true },
        isDedicatedPath = { false },
        findOpenProject = { null },
        openProject = {
          openCalls++
          targetProject
        },
        focusProjectWindow = {
          focusCalls++
        },
        navigate = { _, descriptor ->
          navigatedDescriptor = descriptor
          true
        },
      )

      val handled = interceptor.intercept(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(openCalls).isEqualTo(1)
      assertThat(focusCalls).isEqualTo(1)
      val actualDescriptor = checkNotNull(navigatedDescriptor)
      assertThat(actualDescriptor.file).isSameAs(file)
      assertThat(actualDescriptor.line).isEqualTo(4)
      assertThat(actualDescriptor.column).isEqualTo(2)
    }
  }

  @Test
  fun nonDedicatedProjectIsIgnored() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 1, 0))

      var openCalls = 0
      val interceptor = AgentWorkbenchTerminalHyperlinkNavigationInterceptor(
        selectedSourcePath = { "/tmp/source-project" },
        isDedicatedProject = { false },
        isDedicatedPath = { false },
        findOpenProject = { null },
        openProject = {
          openCalls++
          currentProject
        },
        focusProjectWindow = {},
        navigate = { _, _ -> true },
      )

      val handled = interceptor.intercept(currentProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(openCalls).isZero()
    }
  }

  @Test
  fun dedicatedFramePathSourceIsIgnored() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 1, 0))

      var navigateCalls = 0
      val interceptor = AgentWorkbenchTerminalHyperlinkNavigationInterceptor(
        selectedSourcePath = { "/tmp/dedicated" },
        isDedicatedProject = { true },
        isDedicatedPath = { it == "/tmp/dedicated" },
        findOpenProject = { null },
        openProject = { currentProject },
        focusProjectWindow = {},
        navigate = { _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = interceptor.intercept(currentProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(navigateCalls).isZero()
    }
  }

  @Test
  fun nonFileHyperlinkIsIgnored() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val nonFileHyperlink = HyperlinkInfo { }

      val interceptor = AgentWorkbenchTerminalHyperlinkNavigationInterceptor(
        selectedSourcePath = { "/tmp/source-project" },
        isDedicatedProject = { true },
        isDedicatedPath = { false },
        findOpenProject = { currentProject },
        openProject = { currentProject },
        focusProjectWindow = {},
        navigate = { _, _ -> true },
      )

      val handled = interceptor.intercept(currentProject, nonFileHyperlink, null)

      assertThat(handled).isFalse()
    }
  }
}

private class TestFileHyperlinkInfo(private val openFileDescriptor: OpenFileDescriptor?) : FileHyperlinkInfo {
  override fun getDescriptor(): OpenFileDescriptor? = openFileDescriptor

  override fun navigate(project: Project) {
  }
}
