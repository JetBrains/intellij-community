// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ex.ProjectFrameTypeBean
import com.intellij.openapi.wm.ex.ProjectFrameTypeService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ProjectFrameToolWindowLayoutServiceTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun descriptorLayoutDeserializesPluginXmlNotation() {
    val bean = XmlSerializer.deserialize(
      JDOMUtil.load("""
        <projectFrameToolWindowLayout id="dedicated" applyMode="forceOnce" migrationVersion="4">
          <toolWindow id="Project" register="false"/>
          <toolWindow id="Custom" anchor="right" visible="true" showStripeButton="true"
                      weight="0.25" contentUiType="combo" split="true" sideWeight="0.4"/>
        </projectFrameToolWindowLayout>
      """.trimIndent()),
      ProjectFrameToolWindowLayoutBean::class.java,
    )

    assertThat(bean.id).isEqualTo("dedicated")
    assertThat(bean.applyMode).isEqualTo(ToolWindowLayoutApplyMode.FORCE_ONCE)
    assertThat(bean.migrationVersion).isEqualTo(4)
    assertThat(bean.toolWindows).hasSize(2)

    val projectView = bean.toolWindows[0]
    assertThat(projectView.id).isEqualTo(ToolWindowId.PROJECT_VIEW)
    assertThat(projectView.register).isFalse()

    val custom = bean.toolWindows[1]
    assertThat(custom.id).isEqualTo("Custom")
    assertThat(custom.anchor).isEqualTo("right")
    assertThat(custom.visible).isTrue()
    assertThat(custom.showStripeButton).isTrue()
    assertThat(custom.weight).isEqualTo(0.25f)
    assertThat(custom.contentUiType).isEqualTo("combo")
    assertThat(custom.split).isTrue()
    assertThat(custom.sideWeight).isEqualTo(0.4f)
  }

  @Test
  fun descriptorLayoutCustomizesDefaultLayoutAndMetadata() {
    registerLayoutBean(
      ProjectFrameToolWindowLayoutBean().apply {
        id = "dedicated"
        applyMode = ToolWindowLayoutApplyMode.FORCE_ONCE
        migrationVersion = 4
        toolWindows = listOf(
          suppressedToolWindow(ToolWindowId.PROJECT_VIEW),
          suppressedToolWindow(ToolWindowId.STRUCTURE_VIEW),
          ProjectFrameToolWindowBean().apply {
            id = "Custom"
            anchor = "right"
            visible = true
            showStripeButton = true
            weight = 0.25f
            contentUiType = "combo"
            split = true
            sideWeight = 0.4f
          },
        )
      }
    )

    val profile = service<ToolWindowLayoutProfileService>().getProfile(project = project, profileId = "dedicated", isNewUi = true)

    assertThat(profile).isNotNull()
    assertThat(profile!!.applyMode).isEqualTo(ToolWindowLayoutApplyMode.FORCE_ONCE)
    assertThat(profile.migrationVersion).isEqualTo(4)
    assertThat(profile.layout.getInfo(ToolWindowId.PROJECT_VIEW)).isNull()
    assertThat(profile.layout.getInfo(ToolWindowId.STRUCTURE_VIEW)).isNull()

    val customInfo = profile.layout.getInfo("Custom")
    assertThat(customInfo).isNotNull()
    assertThat(customInfo!!.anchor).isEqualTo(ToolWindowAnchor.RIGHT)
    assertThat(customInfo.isVisible).isTrue()
    assertThat(customInfo.isShowStripeButton).isTrue()
    assertThat(customInfo.weight).isEqualTo(0.25f)
    assertThat(customInfo.contentUiType.name).isEqualTo("combo")
    assertThat(customInfo.isSplit).isTrue()
    assertThat(customInfo.sideWeight).isEqualTo(0.4f)
    assertThat(customInfo.order).isNotEqualTo(-1)
  }

  @Test
  fun descriptorLayoutPlacesAgentSessionsOnDedicatedAnchor() {
    registerLayoutBean(
      ProjectFrameToolWindowLayoutBean().apply {
        id = "dedicated"
        applyMode = ToolWindowLayoutApplyMode.FORCE_ONCE
        migrationVersion = 4
        toolWindows = listOf(
          ProjectFrameToolWindowBean().apply {
            id = AGENT_SESSIONS_TOOL_WINDOW_ID
            anchor = "left"
            visible = true
            showStripeButton = true
            weight = 0.25f
          },
        )
      }
    )

    val profile = service<ToolWindowLayoutProfileService>().getProfile(project = project, profileId = "dedicated", isNewUi = true)

    val agentSessionsInfo = profile!!.layout.getInfo(AGENT_SESSIONS_TOOL_WINDOW_ID)
    assertThat(agentSessionsInfo).isNotNull()
    assertThat(agentSessionsInfo!!.anchor).isEqualTo(ToolWindowAnchor.LEFT)
    assertThat(agentSessionsInfo.isVisible).isTrue()
    assertThat(agentSessionsInfo.isShowStripeButton).isTrue()
    assertThat(agentSessionsInfo.weight).isEqualTo(0.25f)
    assertThat(agentSessionsInfo.order).isNotEqualTo(-1)
  }

  @Test
  fun descriptorLayoutExposesSuppressedToolWindowsByProfileId() {
    registerLayoutBean(
      ProjectFrameToolWindowLayoutBean().apply {
        id = "dedicated"
        toolWindows = listOf(
          suppressedToolWindow(ToolWindowId.PROJECT_VIEW),
          suppressedToolWindow(ToolWindowId.STRUCTURE_VIEW),
        )
      }
    )

    val service = service<ProjectFrameToolWindowLayoutService>()

    assertThat(service.getSuppressedToolWindowIds("dedicated")).containsExactly(ToolWindowId.PROJECT_VIEW, ToolWindowId.STRUCTURE_VIEW)
    assertThat(service.isToolWindowRegistrationSuppressed("dedicated", ToolWindowId.PROJECT_VIEW)).isTrue()
    assertThat(service.isToolWindowRegistrationSuppressed("other", ToolWindowId.PROJECT_VIEW)).isFalse()
    assertThat(service.isToolWindowRegistrationSuppressed(null, ToolWindowId.PROJECT_VIEW)).isFalse()
  }

  @Test
  fun suppressedToolWindowEpDoesNotInstantiateFactory() {
    registerSuppressedToolWindowLayout()
    CountingToolWindowFactory.createdCount = 0
    ExtensionTestUtil.maskExtensions(ToolWindowEP.EP_NAME, listOf(suppressedToolWindowEp()), disposable, fireEvents = false)

    val tasks = runBlocking { computeToolWindowBeans(project, projectFrameTypeId = "DEDICATED") }

    assertThat(tasks).isEmpty()
    assertThat(CountingToolWindowFactory.createdCount).isEqualTo(0)
  }

  @Test
  fun frameTypeWithoutDescriptorKeepsToolWindowEpRegistration() {
    // the layout profile exists, but nothing maps this frame type onto it
    registerLayoutBean(
      ProjectFrameToolWindowLayoutBean().apply {
        id = "dedicated"
        toolWindows = listOf(suppressedToolWindow("Suppressed"))
      }
    )
    CountingToolWindowFactory.createdCount = 0
    ExtensionTestUtil.maskExtensions(ToolWindowEP.EP_NAME, listOf(suppressedToolWindowEp()), disposable, fireEvents = false)

    val tasks = runBlocking { computeToolWindowBeans(project, projectFrameTypeId = "DEDICATED") }

    assertThat(tasks.map { it.id }).containsExactly("Suppressed")
    assertThat(CountingToolWindowFactory.createdCount).isEqualTo(1)
  }

  @Test
  fun nonMatchingFrameTypeKeepsToolWindowEpRegistration() {
    registerSuppressedToolWindowLayout()
    CountingToolWindowFactory.createdCount = 0
    ExtensionTestUtil.maskExtensions(ToolWindowEP.EP_NAME, listOf(suppressedToolWindowEp()), disposable, fireEvents = false)

    val tasks = runBlocking { computeToolWindowBeans(project, projectFrameTypeId = "OTHER") }

    assertThat(tasks.map { it.id }).containsExactly("Suppressed")
    assertThat(CountingToolWindowFactory.createdCount).isEqualTo(1)
  }

  private fun registerSuppressedToolWindowLayout() {
    registerLayoutBean(
      ProjectFrameToolWindowLayoutBean().apply {
        id = "dedicated"
        toolWindows = listOf(suppressedToolWindow("Suppressed"))
      }
    )
    ExtensionTestUtil.maskExtensions(
      ProjectFrameTypeService.EP_NAME,
      listOf(ProjectFrameTypeBean().apply {
        id = "DEDICATED"
        toolWindowLayoutProfile = "dedicated"
      }),
      disposable,
      fireEvents = false,
    )
  }

  private fun registerLayoutBean(bean: ProjectFrameToolWindowLayoutBean) {
    ExtensionTestUtil.maskExtensions(ProjectFrameToolWindowLayoutService.EP_NAME, listOf(bean), disposable, fireEvents = false)
  }
}

private fun suppressedToolWindow(id: String): ProjectFrameToolWindowBean {
  return ProjectFrameToolWindowBean().apply {
    this.id = id
    register = false
  }
}

private fun suppressedToolWindowEp(): ToolWindowEP {
  return ToolWindowEP().apply {
    pluginDescriptor = DefaultPluginDescriptor("test")
    id = "Suppressed"
    anchor = ToolWindowAnchor.LEFT.toString()
    factoryClass = CountingToolWindowFactory::class.java.name
  }
}

private const val AGENT_SESSIONS_TOOL_WINDOW_ID = "agent.workbench.sessions"

class CountingToolWindowFactory : ToolWindowFactory {
  companion object {
    @JvmField
    var createdCount: Int = 0
  }

  init {
    createdCount++
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
