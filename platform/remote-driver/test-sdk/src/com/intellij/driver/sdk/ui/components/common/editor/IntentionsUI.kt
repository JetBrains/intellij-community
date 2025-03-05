package com.intellij.driver.sdk.ui.components.common.editor

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.list
import org.intellij.lang.annotations.Language

fun IdeaFrameUI.intentionList(@Language("xpath") xpath: String? = null, action: JListUiComponent.() -> Unit = {}): JListUiComponent {
  return list(xpath ?: "//div[@class='HeavyWeightWindow']//div[@class='MyList']").apply(action)
}

fun JEditorUiComponent.openIntentions() = driver.invokeAction("ShowIntentionActions")
