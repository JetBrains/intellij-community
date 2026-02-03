package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame


fun Driver.openAiAssistantChat(): IdeaFrameUI = ideFrame {
  rightToolWindowToolbar.aiAssistantButton.click()
}