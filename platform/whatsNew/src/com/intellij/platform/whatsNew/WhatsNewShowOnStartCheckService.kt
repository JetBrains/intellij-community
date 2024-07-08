// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.SystemProperties
import com.intellij.util.application
import java.util.concurrent.atomic.AtomicBoolean

internal class WhatsNewShowOnStartCheckService : ProjectActivity {
  private val ourStarted = AtomicBoolean(false)
  private val isPlaybackMode = SystemProperties.getBooleanProperty("idea.is.playback", false)

  override suspend fun execute(project: Project) {
    if (ourStarted.getAndSet(true)) return
    if (application.isHeadlessEnvironment || application.isUnitTestMode || isPlaybackMode || AppMode.isRemoteDevHost()) return
    logger.info("Checking whether to show the What's New page on startup.")

    val content = WhatsNewContent.getWhatsNewContent()
    logger.info("Got What's New content: $content")
    if (content != null) {
      if (WhatsNewContentVersionChecker.isNeedToShowContent(content).also { logger.info("Should show What's New: $it") }) {
        val whatsNewAction = service<ActionManager>().getAction("WhatsNewAction") as? WhatsNewAction
        whatsNewAction?.openWhatsNew(project)
      }
    }
  }
}

private val logger = logger<WhatsNewShowOnStartCheckService>()