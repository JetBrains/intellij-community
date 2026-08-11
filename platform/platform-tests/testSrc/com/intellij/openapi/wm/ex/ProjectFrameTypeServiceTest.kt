// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ProjectFrameTypeServiceTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun `descriptor deserializes plugin xml notation`() {
    val bean = XmlSerializer.deserialize(
      JDOMUtil.load("""
        <projectFrameType id="AGENT_DEDICATED" reopenWhenHidden="true" toolWindowLayoutProfile="air.dedicated">
          <excludeAction place="MainToolbar" id="MainToolbarVCSGroup"/>
          <excludeAction place="MainToolbar" id="NewUiRunWidget"/>
        </projectFrameType>
      """.trimIndent()),
      ProjectFrameTypeBean::class.java,
    )

    assertThat(bean.id).isEqualTo("AGENT_DEDICATED")
    assertThat(bean.reopenWhenHidden).isTrue()
    assertThat(bean.toolWindowLayoutProfile).isEqualTo("air.dedicated")
    assertThat(bean.excludedActions.map { it.place to it.actionId })
      .containsExactly("MainToolbar" to "MainToolbarVCSGroup", "MainToolbar" to "NewUiRunWidget")
  }

  @Test
  fun `descriptor defaults are inert`() {
    val bean = XmlSerializer.deserialize(JDOMUtil.load("""<projectFrameType id="BARE"/>"""), ProjectFrameTypeBean::class.java)

    assertThat(bean.reopenWhenHidden).isFalse()
    assertThat(bean.toolWindowLayoutProfile).isNull()
    assertThat(bean.excludedActions).isEmpty()
  }

  @Test
  fun `policy is resolved by frame type id`() {
    val service = maskDescriptors(
      descriptor("DEDICATED") {
        reopenWhenHidden = true
        toolWindowLayoutProfile = "dedicated"
        excludedActions = listOf(
          exclusion(ActionPlaces.MAIN_TOOLBAR, "MainToolbarVCSGroup"),
          exclusion(ActionPlaces.MAIN_TOOLBAR, "NewUiRunWidget"),
          exclusion("OtherPlace", "SomethingElse"),
        )
      },
      descriptor("PLAIN"),
    )

    assertThat(service.findDescriptor("DEDICATED")?.id).isEqualTo("DEDICATED")
    assertThat(service.canReopenWhenHidden("DEDICATED")).isTrue()
    assertThat(service.getToolWindowLayoutProfileId("DEDICATED")).isEqualTo("dedicated")
    assertThat(service.getExcludedActionIds("DEDICATED", ActionPlaces.MAIN_TOOLBAR))
      .containsExactly("MainToolbarVCSGroup", "NewUiRunWidget")

    assertThat(service.canReopenWhenHidden("PLAIN")).isFalse()
    assertThat(service.getToolWindowLayoutProfileId("PLAIN")).isNull()
    assertThat(service.getExcludedActionIds("PLAIN", ActionPlaces.MAIN_TOOLBAR)).isEmpty()
  }

  @Test
  fun `undeclared and blank frame types resolve to no policy`() {
    val service = maskDescriptors(descriptor("DEDICATED") { reopenWhenHidden = true })

    for (frameTypeId in listOf(null, "", "   ", "UNKNOWN")) {
      assertThat(service.findDescriptor(frameTypeId)).describedAs(frameTypeId ?: "null").isNull()
      assertThat(service.canReopenWhenHidden(frameTypeId)).describedAs(frameTypeId ?: "null").isFalse()
      assertThat(service.getToolWindowLayoutProfileId(frameTypeId)).describedAs(frameTypeId ?: "null").isNull()
      assertThat(service.getExcludedActionIds(frameTypeId, ActionPlaces.MAIN_TOOLBAR)).describedAs(frameTypeId ?: "null").isEmpty()
    }
  }

  @Test
  fun `declared keys are trimmed and blanks are dropped`() {
    val service = maskDescriptors(
      descriptor("  DEDICATED  ") {
        toolWindowLayoutProfile = "  dedicated  "
        excludedActions = listOf(
          exclusion("  ${ActionPlaces.MAIN_TOOLBAR}  ", "  MainToolbarVCSGroup  "),
          exclusion(ActionPlaces.MAIN_TOOLBAR, "   "),
        )
      }
    )

    assertThat(service.getToolWindowLayoutProfileId("DEDICATED")).isEqualTo("dedicated")
    assertThat(service.getExcludedActionIds("  DEDICATED  ", ActionPlaces.MAIN_TOOLBAR)).containsExactly("MainToolbarVCSGroup")
    assertThat(service.getExcludedActionIds("DEDICATED", "  ")).isEmpty()
  }

  @Test
  fun `duplicate ids keep the first descriptor and report the clash`() {
    val service = maskDescriptors(
      descriptor("DEDICATED") { toolWindowLayoutProfile = "first" },
      descriptor("DEDICATED") { toolWindowLayoutProfile = "second" },
    )

    val loggedErrors = ArrayList<String>()
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String?>, t: Throwable?): MutableSet<Action?> {
        loggedErrors.add(message)
        return Action.NONE
      }
    }) {
      assertThat(service.getToolWindowLayoutProfileId("DEDICATED")).isEqualTo("first")
    }

    assertThat(loggedErrors).singleElement().asString().contains("DEDICATED")
  }

  private fun maskDescriptors(vararg beans: ProjectFrameTypeBean): ProjectFrameTypeService {
    ExtensionTestUtil.maskExtensions(ProjectFrameTypeService.EP_NAME, beans.toList(), disposableRule.disposable)
    return ProjectFrameTypeService()
  }
}

private fun descriptor(id: String, configure: ProjectFrameTypeBean.() -> Unit = {}): ProjectFrameTypeBean {
  return ProjectFrameTypeBean().also {
    it.id = id
    it.configure()
  }
}

private fun exclusion(place: String, actionId: String): ProjectFrameActionExclusionBean {
  return ProjectFrameActionExclusionBean().also {
    it.place = place
    it.actionId = actionId
  }
}
