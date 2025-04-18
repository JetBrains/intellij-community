// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.actions.WhatsNewUtil
import com.intellij.idea.AppMode
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.intellij.util.application
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal interface WhatsNewEnvironmentAccessor {
  val isForceDisabled: Boolean
  suspend fun getWhatsNewContent(): WhatsNewContent?
  fun findAction(): WhatsNewAction?
  suspend fun showWhatsNew(project: Project, action: WhatsNewAction)
  fun isDefaultWhatsNewEnabledAndReadyToShow(): Boolean
}

private class WhatsNewEnvironmentAccessorImpl : WhatsNewEnvironmentAccessor {

  private val isPlaybackMode = SystemProperties.getBooleanProperty("idea.is.playback", false)

  override val isForceDisabled: Boolean
    get() = application.isHeadlessEnvironment
            || application.isUnitTestMode
            || isPlaybackMode
            // TODO: disable for remdev since lux is not ready to open what's new
            || AppMode.isRemoteDevHost()
            // TODO: disable for UI tests since UI tests are not ready for What's new
            || Registry.`is`("expose.ui.hierarchy.url", false)

  override suspend fun getWhatsNewContent() = WhatsNewContent.getWhatsNewContent()
  override fun findAction() = ActionManager.getInstance().getAction("WhatsNewAction") as? WhatsNewAction
  override suspend fun showWhatsNew(project: Project, action: WhatsNewAction) {
    action.openWhatsNew(project)
  }
  override fun isDefaultWhatsNewEnabledAndReadyToShow(): Boolean {
    val updateStrategyCustomization = UpdateStrategyCustomization.getInstance()
    val enabledModernWay = updateStrategyCustomization.showWhatIsNewPageAfterUpdate
    @Suppress("DEPRECATION") val enabledLegacyWay = ApplicationInfoEx.getInstanceEx().isShowWhatsNewOnUpdate

    if (enabledModernWay || enabledLegacyWay) {
      val problem = "This could lead to issues with the Vision-based What's New. Mixing of web-based and Vision-based What's New is not supported."
      if (enabledModernWay) {
        logger.error("${updateStrategyCustomization.javaClass}'s showWhatIsNewPageAfterUpdate is overridden to true. $problem")
      }

      if (enabledLegacyWay) {
        logger.error("show-on-update attribute on the <whatsnew> element in the application info XML is set. $problem")
      }

      if (WhatsNewUtil.isWhatsNewAvailable()) { // if we are really able to show old What's New here, then terminate.
        return true
      }
    }

    return false
  }
}

@Service
internal class WhatsNewStatus {
  private val isContentAvailableFlag = AtomicBoolean()
  var isContentAvailable: Boolean
    get() = isContentAvailableFlag.get()
    set(value) = isContentAvailableFlag.set(value)
}

internal class WhatsNewShowOnStartCheckService(private val environment: WhatsNewEnvironmentAccessor) : ProjectActivity {
  @Suppress("unused") // used by the component container
  constructor() : this(WhatsNewEnvironmentAccessorImpl())

  private val wasStarted = AtomicBoolean(false)

  override suspend fun execute(project: Project) {
    if (wasStarted.getAndSet(true)) return
    if (environment.isForceDisabled) return
    logger.info("Checking whether to show the What's New page on startup.")

    // a bit hacky workaround but now we don't have any tools to forward local startup activities to a controller
    val clientId = ClientSessionsManager.getAppSessions(ClientKind.CONTROLLER).firstOrNull()?.clientId ?: ClientId.localId
    withContext(clientId.asContextElement()) {
      val content = environment.getWhatsNewContent()
      logger.info("Got What's New content: $content")
      if (content != null) {
        serviceAsync<WhatsNewStatus>().isContentAvailable = content.isAvailable()
        if (WhatsNewContentVersionChecker.isNeedToShowContent(content).also { logger.info("Should show What's New: $it") }) {
          val whatsNewAction = environment.findAction()
          if (whatsNewAction != null) {
            if (environment.isDefaultWhatsNewEnabledAndReadyToShow()) {
              return@withContext
            }

            val activityTracker = ProjectInitializationDiagnosticService.registerTracker(project, "OpenWhatsNewOnStart")
            environment.showWhatsNew(project, whatsNewAction)
            activityTracker.activityFinished()
          }
        }
      }
    }
  }
}

private val logger = logger<WhatsNewShowOnStartCheckService>()
