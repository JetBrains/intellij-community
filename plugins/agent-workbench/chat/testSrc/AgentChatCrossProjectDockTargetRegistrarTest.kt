// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.impl.DockableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.ui.docking.DockableContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Dimension
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
internal class AgentChatCrossProjectDockTargetRegistrarTest {
  private val dedicatedProject = testProject("dedicated")
  private val sourceProject = testProject("source")

  @Test
  fun matchingOpenSourceProjectRegistersProxyContainerAndForwardsDrop() {
    val targetContainer = TestDockContainer()
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      openProjectsProvider = { arrayOf(sourceProject) },
      projectIdentityPath = { project -> if (project === sourceProject) "/repo" else null },
      dockContainerProvider = { project -> if (project === sourceProject) targetContainer else null },
      registeredContainers = registeredContainers,
    )

    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "/repo"))

    assertThat(registration).isNotNull
    assertThat(registeredContainers).hasSize(1)
    assertThat(registeredContainers.single().project).isSameAs(dedicatedProject)
    val proxyContainer = registeredContainers.single().container
    val content = chatContent(projectPath = "/repo")
    val point = relativePoint()
    assertThat(proxyContainer.getContentResponse(content, point)).isEqualTo(ContentResponse.ACCEPT_MOVE)
    assertThat(proxyContainer.getContentResponse(chatContent(projectPath = "/other"), point)).isEqualTo(ContentResponse.DENY)

    proxyContainer.add(content, point)

    assertThat(targetContainer.addedContents).containsExactly(content)
    Disposer.dispose(checkNotNull(registration))
  }

  @Test
  fun sourceProjectOpenedAfterRegistrationAcceptsDrop() {
    val openProjects = mutableListOf<Project>()
    val targetContainer = TestDockContainer()
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      openProjectsProvider = { openProjects.toTypedArray() },
      projectIdentityPath = { project -> if (project === sourceProject) "/repo" else null },
      dockContainerProvider = { project -> if (project === sourceProject) targetContainer else null },
      registeredContainers = registeredContainers,
    )
    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "/repo"))
    val proxyContainer = registeredContainers.single().container
    val content = chatContent(projectPath = "/repo")

    assertThat(proxyContainer.getContentResponse(content, relativePoint())).isEqualTo(ContentResponse.DENY)

    openProjects.add(sourceProject)

    assertThat(proxyContainer.getContentResponse(content, relativePoint())).isEqualTo(ContentResponse.ACCEPT_MOVE)
    Disposer.dispose(checkNotNull(registration))
  }

  @Test
  fun pathEquivalenceFallbackAcceptsDrop() {
    val targetContainer = TestDockContainer()
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      openProjectsProvider = { arrayOf(sourceProject) },
      projectIdentityPath = { "/repo/from-manager.ipr" },
      isPathEquivalentToProject = { project, path -> project === sourceProject && path == Path.of("/repo") },
      dockContainerProvider = { project -> if (project === sourceProject) targetContainer else null },
      registeredContainers = registeredContainers,
    )

    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "/repo"))

    assertThat(registeredContainers.single().container.getContentResponse(chatContent(projectPath = "/repo"), relativePoint()))
      .isEqualTo(ContentResponse.ACCEPT_MOVE)
    Disposer.dispose(checkNotNull(registration))
  }

  @Test
  fun otherProjectFrameRejectsDrop() {
    val targetContainer = TestDockContainer()
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      openProjectsProvider = { arrayOf(sourceProject) },
      projectIdentityPath = { "/other" },
      isPathEquivalentToProject = { _, _ -> false },
      dockContainerProvider = { targetContainer },
      registeredContainers = registeredContainers,
    )

    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "/repo"))

    assertThat(registeredContainers.single().container.getContentResponse(chatContent(projectPath = "/repo"), relativePoint()))
      .isEqualTo(ContentResponse.DENY)
    assertThat(targetContainer.addedContents).isEmpty()
    Disposer.dispose(checkNotNull(registration))
  }

  @Test
  fun nonAgentContentIsRejected() {
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      openProjectsProvider = { arrayOf(sourceProject) },
      projectIdentityPath = { project -> if (project === sourceProject) "/repo" else null },
      registeredContainers = registeredContainers,
    )

    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "/repo"))

    assertThat(registeredContainers.single().container.getContentResponse(TestDockableContent(), relativePoint()))
      .isEqualTo(ContentResponse.DENY)
    Disposer.dispose(checkNotNull(registration))
  }

  @Test
  fun sourceProjectEditorDoesNotRegisterProxyContainer() {
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(
      projectIdentityPath = { project -> if (project === sourceProject) "/repo" else null },
      registeredContainers = registeredContainers,
    )

    val registration = registrar.register(sourceProject, chatFile(projectPath = "/repo"))

    assertThat(registration).isNull()
    assertThat(registeredContainers).isEmpty()
  }

  @Test
  fun blankSourcePathDoesNotRegisterProxyContainer() {
    val registeredContainers = mutableListOf<RegisteredContainer>()
    val registrar = testRegistrar(registeredContainers = registeredContainers)

    val registration = registrar.register(dedicatedProject, chatFile(projectPath = "   "))

    assertThat(registration).isNull()
    assertThat(registeredContainers).isEmpty()
  }
}

private fun testRegistrar(
  openProjectsProvider: () -> Array<Project> = { emptyArray() },
  projectIdentityPath: (Project) -> String? = { "/repo" },
  isPathEquivalentToProject: (Project, Path) -> Boolean = { _, _ -> false },
  dockContainerProvider: (Project) -> DockContainer? = { TestDockContainer() },
  registeredContainers: MutableList<RegisteredContainer>,
): AgentChatCrossProjectDockTargetRegistrar {
  return AgentChatCrossProjectDockTargetRegistrar(
    openProjectsProvider = openProjectsProvider,
    projectIdentityPath = projectIdentityPath,
    isPathEquivalentToProject = isPathEquivalentToProject,
    dockContainerProvider = dockContainerProvider,
    registerContainer = { project, container, _ ->
      registeredContainers.add(RegisteredContainer(project, container))
      true
    },
  )
}

private data class RegisteredContainer(
  val project: Project,
  val container: DockContainer,
)

private fun chatFile(projectPath: String): AgentChatVirtualFile {
  return AgentChatVirtualFile(
    projectPath = projectPath,
    threadIdentity = "CODEX:thread-1",
    shellCommand = listOf("codex", "resume", "thread-1"),
    threadId = "thread-1",
    threadTitle = "Thread 1",
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
  )
}

private fun chatContent(projectPath: String): DockableEditor {
  val file = chatFile(projectPath)
  return DockableEditor(
    img = null,
    file = file,
    presentation = Presentation(file.name),
    preferredSize = Dimension(100, 100),
    isPinned = false,
    isSingletonEditorInWindow = false,
  )
}

private fun relativePoint(): RelativePoint = RelativePoint(JPanel(), Point())

private fun testProject(name: String, disposed: Boolean = false): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
      "isDisposed" -> disposed
      "toString" -> "Project($name)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}

private class TestDockContainer : DockContainer {
  private val component = JPanel()
  val addedContents = mutableListOf<DockableContent<*>>()

  override fun getAcceptArea(): RelativeRectangle = RelativeRectangle(component, Rectangle(0, 0, 100, 100))

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint): ContentResponse = ContentResponse.ACCEPT_MOVE

  override fun getContainerComponent(): JComponent = component

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    addedContents.add(content)
  }

  override fun isEmpty(): Boolean = false

  override fun isDisposeWhenEmpty(): Boolean = false
}

private class TestDockableContent : DockableContent<String> {
  override fun getKey(): String = "test"

  override fun getPreviewImage(): Image? = null

  override fun getDockContainerType(): String = "test"

  override fun getPreferredSize(): Dimension = Dimension(100, 100)

  override fun close() = Unit

  override fun getPresentation(): Presentation = Presentation("test")
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}
