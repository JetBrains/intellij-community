// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.workspace.Workspace
import com.intellij.openapi.workspace.workspace
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.setPluginClassLoaderForMainAndSubPlugins
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class ProjectWorkspaceContainerTest {
  private val firstProjectFixture = projectFixture()
  private val secondProjectFixture = projectFixture()

  @TempDir
  lateinit var pluginsDir: Path

  private val manager: ProjectWorkspaceManagerImpl
    get() = ProjectWorkspaceManagerImpl.getInstance()

  @Test
  fun `a light service has one instance for each workspace`() {
    val project = firstProjectFixture.get()
    val workspace = project.workspace

    assertThat(workspace).isInstanceOf(WorkspaceAsProject::class.java)
    assertThat(project.workspace).isSameAs(workspace)
    assertThat(workspace.projects).containsExactly(project)
    assertThat(workspace.service<TestWorkspaceLightService>()).isSameAs(workspace.service<TestWorkspaceLightService>())

    val otherProject = secondProjectFixture.get()
    val otherWorkspace = otherProject.workspace
    assertThat(otherWorkspace).isNotSameAs(workspace)
    assertThat(otherWorkspace.service<TestWorkspaceLightService>()).isNotSameAs(workspace.service<TestWorkspaceLightService>())
    assertThat(manager.liveWorkspaces()).contains(workspace, otherWorkspace)
  }

  @Test
  fun `a descriptor service is registered`() {
    val workspace = firstProjectFixture.get().workspace
    registerPlugin(
      workspace,
      """<workspaceService serviceInterface="${TestWorkspaceService::class.java.name}"
                           serviceImplementation="${TestWorkspaceServiceImpl::class.java.name}"/>""",
    )

    assertThat(workspace.getService(TestWorkspaceService::class.java)).isInstanceOf(TestWorkspaceServiceImpl::class.java)
  }

  @Test
  fun `the four constructors`() {
    val workspace = firstProjectFixture.get().workspace

    assertThat(workspace.service<TestNoArgService>()).isNotNull()
    assertThat(workspace.service<TestScopeService>().scope).isNotNull()
    assertThat(workspace.service<TestWorkspaceArgService>().workspace).isSameAs(workspace)
    val withScope = workspace.service<TestWorkspaceAndScopeService>()
    assertThat(withScope.workspace).isSameAs(workspace)
    assertThat(withScope.scope).isNotNull()

    val instance = workspace.instantiateClass(TestWorkspaceArgClass::class.java, PluginManagerCore.CORE_ID)
    assertThat(instance!!.workspace).isSameAs(workspace)
  }

  @Test
  fun `a client service logs an error`() {
    val workspace = firstProjectFixture.get().workspace
    val errors = ArrayList<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors.add(message)
        return Action.NONE
      }
    }

    LoggedErrorProcessor.executeWith(processor).use {
      registerPlugin(workspace, """<workspaceService serviceImplementation="${TestClientWorkspaceService::class.java.name}" client="all"/>""")
    }

    assertThat(errors).anySatisfy { assertThat(it).contains("client") }
    assertThat(workspace.getService(TestClientWorkspaceService::class.java)).isNull()
  }

  private fun registerPlugin(workspace: Workspace, xml: String) {
    val descriptor = loadDescriptorInTest(plugin { extensions(xml) }, pluginsDir)
    setPluginClassLoaderForMainAndSubPlugins(descriptor, javaClass.classLoader)
    workspace.getComponentManagerImpl().registerComponents(sequenceOf(descriptor), ApplicationManager.getApplication())
  }
}

@Service(Service.Level.WORKSPACE)
private class TestWorkspaceLightService

private interface TestWorkspaceService

private class TestWorkspaceServiceImpl : TestWorkspaceService

private class TestClientWorkspaceService

@Service(Service.Level.WORKSPACE)
private class TestNoArgService

@Service(Service.Level.WORKSPACE)
private class TestScopeService(val scope: CoroutineScope)

@Service(Service.Level.WORKSPACE)
private class TestWorkspaceArgService(val workspace: Workspace)

@Service(Service.Level.WORKSPACE)
private class TestWorkspaceAndScopeService(val workspace: Workspace, val scope: CoroutineScope)

private class TestWorkspaceArgClass(val workspace: Workspace)
