// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion.vision

import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.trialPromotion.TrialPromotionBundle
import com.intellij.platform.trialPromotion.TrialStateService.TrialProgressData
import com.intellij.platform.trialPromotion.TrialTabContent
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TrialTabVisionContent(page: Page) : TrialTabContent() {
  companion object {
    private const val THEME_KEY = "\$__VISION_PAGE_SETTINGS_THEME__$"
    private const val DARK_THEME = "dark"
    private const val LIGHT_THEME = "light"

    private const val LANG_KEY = "\$__VISION_PAGE_SETTINGS_LANGUAGE_CODE__$"
    private const val DEFAULT_LANG = "en-us"

    private const val ZOOM_KEY = "\$__VISION_PAGE_SETTINGS_ZOOM_IN_ACTION__$"
    private const val ZOOM_VALUE = "trial.vision.zoom"

    private const val GIF_KEY = "\$__VISION_PAGE_SETTINGS_GIF_PLAYER_ACTION__$"
    private const val GIF_VALUE = "trial.vision.gif"

    private const val DAYS_LEFT_KEY = "\$__VISION_PAGE_SETTINGS_TRIAL_DAYS_LEFT__$"
    private const val DAYS_TOTAL_KEY = "\$__VISION_PAGE_SETTINGS_TRIAL_DAYS_TOTAL__$"
  }

  private val content: String = page.html
  private val actionAllowList: Set<String> = page.actions.map { it.value }.toSet()
  private val visionActionIds = setOf(ZOOM_VALUE, GIF_VALUE)

  private val publicVarsPattern = page.publicVars.distinctBy { it.value }
    .joinToString("|") { Regex.escape(it.value) }.toRegex()

  override suspend fun show(project: Project, dataContext: DataContext?, trialProgressData: TrialProgressData) {
    if (!JBCefApp.isSupported()) {
      logger.error("Trial editor tab: can't be shown. JBCefApp isn't supported")
      return
    }

    withContext(Dispatchers.EDT) {
      logger.info("Opening a Trial tab in editor")
      val request = getContentRequest(dataContext, trialProgressData)
      writeIntentReadAction {
        HTMLEditorProvider.openEditor(project, TrialPromotionBundle.message("trial.editor.tab.title"), request)
      }
      // TODO: statistics
    }
  }

  override suspend fun isAvailable(): Boolean = true

  private fun processContent(trialProgressData: TrialProgressData): String =
    content.replace(publicVarsPattern) {
      when (it.value) {
        GIF_KEY -> GIF_VALUE
        ZOOM_KEY -> ZOOM_VALUE
        DAYS_LEFT_KEY -> trialProgressData.daysRemaining.toString()
        DAYS_TOTAL_KEY -> trialProgressData.daysTotal.toString()
        THEME_KEY -> if (StartupUiUtil.isDarkTheme) DARK_THEME else LIGHT_THEME
        LANG_KEY -> LocalizationStateService.getInstance()?.selectedLocale?.lowercase() ?: run {
          logger.error("Cannot get a LocalizationStateService instance. Default to $DEFAULT_LANG locale.")
          DEFAULT_LANG
        }
        else -> it.value
      }
    }

  private fun getContentRequest(dataContext: DataContext?, trialProgressData: TrialProgressData): HTMLEditorProvider.Request {
    // values such as theme or language might change after initialization
    val html = processContent(trialProgressData)
    return HTMLEditorProvider.Request.html(html)
      .withQueryHandler(getQueryHandler(dataContext))
  }

  private fun getQueryHandler(dataContext: DataContext?): HTMLEditorProvider.JsQueryHandler? {
    dataContext ?: return null

    return object : HTMLEditorProvider.JsQueryHandler {
      override suspend fun query(id: Long, request: String): String = queryImpl(request).toString()

      private suspend fun queryImpl(request: String): Boolean {
        if (!actionAllowList.contains(request)) {
          if (visionActionIds.contains(request)) {
            logger.trace { "Trial: action $request performed" }
            return true
          }

          logger.trace { "Trial: action $request is not allowed" }
          return false
        }

        if (request.isNotEmpty()) {
          val action = service<ActionManager>().getAction(request)
          if (action != null) {
            withContext(Dispatchers.EDT) {
              val event = AnActionEvent.createEvent(
                action,
                dataContext,
                /*presentation =*/ null,
                /*place =*/ "",
                ActionUiKind.NONE,
                /*event =*/ null,
              )
              action.actionPerformed(event)
              logger.trace { "Trial: action $request performed" }
            }
            return true
          }
          else {
            logger.trace { "Trial: action $request not found" }
          }
        }
        return false
      }
    }
  }
}

private val logger = logger<TrialTabVisionContent>()
