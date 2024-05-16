// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.WhatsNewAction
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.openEditor
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.JsQueryHandler
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request.Companion.url
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.whatsNew.reaction.FUSReactionChecker
import com.intellij.platform.whatsNew.reaction.ReactionsPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.application
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.let
import kotlin.run
import kotlin.text.isNotEmpty

class WhatsNewAction : AnAction(), com.intellij.openapi.project.DumbAware {
  companion object {
    private val DataContext.project: Project?
      get() = CommonDataKeys.PROJECT.getData(this)

    private val LOG = logger<WhatsNewAction>()

    private const val PLACE = "WhatsNew"
    private const val TEST_KEY = "whats.new.test.mode"
    private const val REACTIONS_STATE = "whatsnew.reactions.state"


    private val actionWhiteList = listOf("SearchEverywhere", "ChangeLaf", "ChangeIdeScale",
                                         "SettingsSyncOpenSettingsAction", "BuildWholeSolutionAction",
                                         "GitLab.Open.Settings.Page",
                                         "AIAssistant.ToolWindow.ShowOrFocus", "ChangeMainToolbarColor",
                                         "ShowEapDiagram", "multilaunch.RunMultipleProjects",
                                         "EfCore.Shared.OpenQuickEfCoreActionsAction",
                                         "OpenNewTerminalEAP", "CollectionsVisualizerEAP", "ShowDebugMonitoringToolEAP",
                                         "LearnMoreStickyScrollEAP", "NewRiderProject", "BlazorHotReloadEAP")

    private fun getHandler(dataContext: DataContext?): JsQueryHandler? {
      dataContext ?: return null

      return object : JsQueryHandler {
        override suspend fun query(id: Long, request: String): String {
          val contains = actionWhiteList.contains(request)
          if (!contains) {
            if (LOG.isTraceEnabled) {
              LOG.trace("EapWhatsNew action $request not allowed")
            }
            WhatsNewCounterUsageCollector.actionNotAllowed(dataContext.project, request)
            return "false"
          }

          if (request.isNotEmpty()) {
            ActionManager.getInstance().getAction(request)?.let {
              withContext(Dispatchers.EDT) {
                it.actionPerformed(AnActionEvent.createFromAnAction(it, null, PLACE, dataContext))
                if (LOG.isTraceEnabled) {
                  LOG.trace("EapWhatsNew action $request performed")
                }
                WhatsNewCounterUsageCollector.actionPerformed(dataContext.project, request)
              }
              return "true"
            } ?: run {
              if (LOG.isTraceEnabled) {
                LOG.trace("EapWhatsNew action $request not found")
              }
              WhatsNewCounterUsageCollector.actionNotFound(dataContext.project, request)
            }
          }
          return "false"
        }
      }
    }

    private val reactionChecker = FUSReactionChecker(REACTIONS_STATE)

    fun refresh() {
      reactionChecker.clearLikenessState()
      if (LOG.isTraceEnabled) {
        LOG.trace("EapWhatsNew reaction refresh")
      }
    }

    private val isEap: Boolean
      get() = if (Registry.`is`(TEST_KEY)) true else ApplicationInfoEx.getInstanceEx().isEAP

    fun openWhatsNew(project: Project) {
      if (!isEap) {
        openWhatsNewPage(project)
        if (LOG.isTraceEnabled) {
          LOG.trace("EapWhatsNew: it's not EAP version")
        }
        return
      }

      val dataContextFromFocusAsync = DataManager.getInstance().dataContextFromFocusAsync
      if (dataContextFromFocusAsync.isSucceeded) {
        dataContextFromFocusAsync.onSuccess { dataContext ->
          val queryHandler = getHandler(dataContext)
          openWhatsNewPage(project, false, queryHandler)
        }.onError {
          openWhatsNewPage(project)
        }
        return
      }
      openWhatsNewPage(project)
    }

    private fun openWhatsNewPage(project: Project, url: String, byClient: Boolean = false, queryHandler: JsQueryHandler?) {
      check(JBCefApp.isSupported()) { "JCEF is not supported on this system" }
      val parameters = HashMap<String, String>()
      parameters["var"] = "embed"
      if (StartupUiUtil.isDarkTheme) {
        parameters["theme"] = "dark"
      }
      val locale = Locale.getDefault()
      if (locale != null) {
        parameters["lang"] = locale.toLanguageTag().lowercase()
      }
      val request = url(newFromEncoded(url).addParameters(parameters).toExternalForm())
      try {
        WhatsNewAction::class.java.getResourceAsStream("whatsNewTimeoutText.html").use { stream ->
          if (stream != null) {
            request.withTimeoutHtml(String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("__THEME__",
                                                                                                  if (StartupUiUtil.isDarkTheme) "theme-dark" else "")
                                      .replace("__TITLE__", IdeBundle.message("whats.new.timeout.title"))
                                      .replace("__MESSAGE__", IdeBundle.message("whats.new.timeout.message"))
                                      .replace("__ACTION__", IdeBundle.message("whats.new.timeout.action", url)))
          }
        }
      }
      catch (e: IOException) {
        Logger.getInstance(WhatsNewAction::class.java).error(e)
      }
      request.withQueryHandler(queryHandler)
      val title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().fullProductName)

      openEditor(project, title, request)?.let {
        FileEditorManager.getInstance(project).addTopComponent(it, ReactionsPanel.createPanel(PLACE, reactionChecker))
        WhatsNewCounterUsageCollector.openedPerformed(project, byClient)

        WhatsNewContentVersionChecker.saveLastShownUrl(url)

        val disposable = Disposer.newDisposable(project)
        val busConnection = application.messageBus.connect(disposable)
        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            if (it.file == file) {
              WhatsNewCounterUsageCollector.closedPerformed(project)
              Disposer.dispose(disposable)
            }
          }
        })
      }
    }

    private fun openWhatsNewPage(project: Project?, byClient: Boolean = false, queryHandler: JsQueryHandler? = null) {
      val whatsNewUrl = WhatsNewContentVersionChecker.getUrl() ?: return

      if (LOG.isTraceEnabled) {
        LOG.trace("EapWhatsNew: openWhatsNewPage. queryHandler ${if (queryHandler != null) "enabled" else "disabled"}")
      }

      if (project != null && JBCefApp.isSupported()) {
        openWhatsNewPage(project, whatsNewUrl, byClient, queryHandler)
      }
      else {
        if (LOG.isTraceEnabled) {
          LOG.trace("EapWhatsNew: can't be shown. JBCefApp isn't supported")
        }
      }
    }
  }

  init {
    templatePresentation.text = WhatsNewBundle.message("EapWhatsNewAction.text")
    templatePresentation.description = WhatsNewBundle.message("EapWhatsNewAction.description")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val available = WhatsNewContentVersionChecker.getUrl() != null
    e.presentation.isEnabledAndVisible = available
    if (available) {
      e.presentation.setText(IdeBundle.messagePointer("whats.new.action.custom.text", ApplicationNamesInfo.getInstance().fullProductName))
      e.presentation.setDescription(
        IdeBundle.messagePointer("whats.new.action.custom.description", ApplicationNamesInfo.getInstance().fullProductName))
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    openWhatsNewPage(e.project, true, if (isEap) getHandler(e.dataContext) else null)
  }
}

internal enum class OpenedType { Auto, ByClient }

@Suppress("EnumEntryName")
internal enum class ActionFailedReason { Not_Allowed, Not_Found }


@Suppress("CompanionObjectInExtension")
internal object WhatsNewCounterUsageCollector : CounterUsagesCollector() {
  private val eventLogGroup: EventLogGroup = EventLogGroup("whatsnew", 1)

  private val opened = eventLogGroup.registerEvent("tab_opened", EventFields.Enum(("type"), OpenedType::class.java))
  private val closed = eventLogGroup.registerEvent("tab_closed")
  private val actionId = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  private val perform = eventLogGroup.registerEvent("action_performed", actionId)
  private val failed = eventLogGroup.registerEvent("action_failed", actionId, EventFields.Enum(("type"), ActionFailedReason::class.java))

  fun openedPerformed(project: Project?, byClient: Boolean) {
    opened.log(project, if (byClient) OpenedType.ByClient else OpenedType.Auto)
    LegacyRiderWhatsNewCounterUsagesCollector.opened.log(project, if (byClient) OpenedType.ByClient else OpenedType.Auto)
  }

  fun closedPerformed(project: Project?) {
    closed.log(project)
    LegacyRiderWhatsNewCounterUsagesCollector.closed.log(project)
  }

  fun actionPerformed(project: Project?, id: String) {
    perform.log(project, id)
    LegacyRiderWhatsNewCounterUsagesCollector.perform.log(project, id)
  }

  fun actionNotAllowed(project: Project?, id: String) {
    failed.log(project, id, ActionFailedReason.Not_Allowed)
    LegacyRiderWhatsNewCounterUsagesCollector.failed.log(project, id, ActionFailedReason.Not_Allowed)
  }

  fun actionNotFound(project: Project?, id: String) {
    failed.log(project, id, ActionFailedReason.Not_Found)
    LegacyRiderWhatsNewCounterUsagesCollector.failed.log(project, id, ActionFailedReason.Not_Found)
  }

  override fun getGroup(): EventLogGroup {
    return eventLogGroup
  }
}

internal object LegacyRiderWhatsNewCounterUsagesCollector : CounterUsagesCollector() {
  private val eventLogGroup: EventLogGroup = EventLogGroup("rider.whatsnew.eap", 3)

  internal val opened = eventLogGroup.registerEvent("tab_opened", EventFields.Enum(("type"), OpenedType::class.java))
  internal val closed = eventLogGroup.registerEvent("tab_closed")
  internal val actionId = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  internal val perform = eventLogGroup.registerEvent("action_performed", actionId)
  internal val failed = eventLogGroup.registerEvent("action_failed", actionId, EventFields.Enum(("type"), ActionFailedReason::class.java))

  override fun getGroup(): EventLogGroup {
    return eventLogGroup
  }
}
