// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.idea.AppMode
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.intellij.util.application
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal class WhatsNewShowOnStartCheckService : ProjectActivity {
  private val ourStarted = AtomicBoolean(false)
  private val isPlaybackMode = SystemProperties.getBooleanProperty("idea.is.playback", false)

  override suspend fun execute(project: Project) {
    if (ourStarted.getAndSet(true)) return
    if (application.isHeadlessEnvironment
        || application.isUnitTestMode
        || isPlaybackMode
        // TODO: disable for remdev since lux is not ready to open what's new
        || AppMode.isRemoteDevHost()
        // TODO: disable for UI tests since UI tests are not ready for What's new
        || Registry.`is`("expose.ui.hierarchy.url", false)) return
    logger.info("Checking whether to show the What's New page on startup.")

    // a bit hacky workaround but now we don't have any tools to forward local startup activities to a controller
    val clientId = ClientSessionsManager.getAppSessions(ClientKind.CONTROLLER).firstOrNull()?.clientId ?: ClientId.localId
    withContext(clientId.asContextElement()) {
      val content = WhatsNewContent.getWhatsNewContent()
      logger.info("Got What's New content: $content")
      if (content != null) {
        if (WhatsNewContentVersionChecker.isNeedToShowContent(content).also { logger.info("Should show What's New: $it") }) {
          val whatsNewAction = service<ActionManager>().getAction("WhatsNewAction") as? WhatsNewAction
          if (whatsNewAction == null) {
            val activityTracker = ProjectInitializationDiagnosticService.registerTracker(project, "OpenWhatsNewOnStart");
            whatsNewAction?.openWhatsNew(project)
            activityTracker.activityFinished()
          }
        }
      }
    }
  }
}

private val logger = logger<WhatsNewShowOnStartCheckService>()