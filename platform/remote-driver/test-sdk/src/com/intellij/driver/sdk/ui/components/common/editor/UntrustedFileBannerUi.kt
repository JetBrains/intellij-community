package com.intellij.driver.sdk.ui.components.common.editor

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionHyperlinkLabel

/**
 * The editor banner of a file in the safe mode.
 */
fun IdeaFrameUI.untrustedFileBanner(action: UntrustedFileBannerUi.() -> Unit = {}): UntrustedFileBannerUi =
  editorTabs().x(UntrustedFileBannerUi::class.java) {
    byType("com.intellij.ide.trustedProjects.impl.UntrustedFileEditorNotificationPanel")
  }.apply(action)

class UntrustedFileBannerUi(data: ComponentData) : UiComponent(data) {
  val trustFileLink: JLabelUiComponent = actionHyperlinkLabel("Trust file…")
}
