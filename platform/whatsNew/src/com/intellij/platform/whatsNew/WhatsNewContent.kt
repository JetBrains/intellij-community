// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.IdeBundle
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.JsQueryHandler
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request.Companion.html
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request.Companion.url
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.whatsNew.collectors.WhatsNewCounterUsageCollector
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal sealed class WhatsNewContent {
  companion object {
    private val DataContext.project: Project?
      get() = CommonDataKeys.PROJECT.getData(this)

    suspend fun getWhatsNewContent(): WhatsNewContent? {
      return if (WhatsNewInVisionContentProvider.getInstance().isAvailable()) {
        WhatsNewVisionContent(WhatsNewInVisionContentProvider.getInstance().getContent().entities.first())
      } else {
        ExternalProductResourceUrls.getInstance().whatIsNewPageUrl?.toDecodedForm()?.let { WhatsNewUrlContent(it) }
      }
    }
  }

  // Year and release have to be strings, because this is the ApplicationInfo.xml format. "release" might be a string like "2.1".
  data class ContentVersion(val year: String, val release: String, val eap: Int?, val hash: String?) : Comparable<ContentVersion> {

    companion object {
      fun parse(text: String): ContentVersion? {
        val components = text.split("-")
        when (components.size) {
          3 -> {
            val year = components[0]
            val release = components[1]
            val eap = components[2].toIntOrNull()
            if (year.isNotEmpty() && release.isNotEmpty() && eap != null)
              return ContentVersion(year, release, eap, null)
          }
          4 -> {
            val year = components[0]
            val release = components[1]
            val eap = components[2].toIntOrNull()
            val hash = components[3]
            if (year.isNotEmpty() && release.isNotEmpty() && eap != null)
              return ContentVersion(year, release, eap, hash)
          }
        }

        return null
      }
    }

    override fun toString(): String {
      if (hash != null) {
        return "$year-$release-$eap-$hash"
      } else {
        return "$year-$release-$eap"
      }
    }

    fun releaseInfoEquals(other: ContentVersion): Boolean =
      year == other.year && release == other.release && eap == other.eap

    override operator fun compareTo(other: ContentVersion): Int {
      val result = compareValuesBy(this, other, { it.year }, { Version.parseVersion(it.release) })
      return when {
        result != 0 -> result
        eap == null && other.eap != null -> 1
        eap != null && other.eap == null -> -1
        else -> compareValuesBy(this, other) { it.eap }
      }
    }
  }

  abstract fun getRequest(dataContext: DataContext?): HTMLEditorProvider.Request
  abstract fun getActionWhiteList(): Set<String>
  abstract fun getVersion(): ContentVersion?
  abstract suspend fun isAvailable(): Boolean

  protected fun getHandler(dataContext: DataContext?): JsQueryHandler? {
    dataContext ?: return null

    return object : JsQueryHandler {
      override suspend fun query(id: Long, request: String): String {
        val contains = getActionWhiteList().contains(request)
        if (!contains) {
          logger.trace { "EapWhatsNew action $request not allowed" }
          WhatsNewCounterUsageCollector.actionNotAllowed(dataContext.project, request)
          return "false"
        }

        if (request.isNotEmpty()) {
          service<ActionManager>().getAction(request)?.let {
            withContext(Dispatchers.EDT) {
              it.actionPerformed(AnActionEvent.createFromAnAction(it, null, WhatsNewAction.PLACE, dataContext))
              logger.trace { "EapWhatsNew action $request performed" }
              WhatsNewCounterUsageCollector.actionPerformed(dataContext.project, request)
            }
            return "true"
          } ?: run {
            logger.trace { "EapWhatsNew action $request not found" }
            WhatsNewCounterUsageCollector.actionNotFound(dataContext.project, request)
          }
        }
        return "false"
      }
    }
  }
}

internal class WhatsNewUrlContent(val url: String) : WhatsNewContent() {
  companion object {
    val LOG = logger<WhatsNewUrlContent>()
    private val actionWhiteList = mutableSetOf("SearchEverywhere", "ChangeLaf", "ChangeIdeScale",
                                               "SettingsSyncOpenSettingsAction", "BuildWholeSolutionAction",
                                               "GitLab.Open.Settings.Page",
                                               "AIAssistant.ToolWindow.ShowOrFocus", "ChangeMainToolbarColor",
                                               "ShowEapDiagram", "multilaunch.RunMultipleProjects",
                                               "EfCore.Shared.OpenQuickEfCoreActionsAction",
                                               "OpenNewTerminalEAP", "CollectionsVisualizerEAP", "ShowDebugMonitoringToolEAP",
                                               "LearnMoreStickyScrollEAP", "NewRiderProject", "BlazorHotReloadEAP")
  }

  private val linkRegEx = "^https://www\\.jetbrains\\.com/[a-zA-Z]+/whatsnew(-eap)?/(\\d+)-(\\d+)-(\\d+)/$".toRegex()

  private fun parseUrl(link: String): ContentVersion? {
    val parseResult = linkRegEx.matchEntire(link)?.let {
      val year = it.groups[it.groups.size - 3]?.value ?: return@let null
      val release = it.groups[it.groups.size - 2]?.value ?: return@let null
      val eap = it.groups[it.groups.size - 1]?.value?.toInt() ?: return@let null
      ContentVersion(year, release, eap, null)
    }

    if (parseResult == null) {
      LOG.warn("Cannot parse IDE version for What's New content from URL: \"$link\".")
    }

    return when {
      parseResult != null -> parseResult
      ApplicationInfo.getInstance().isEAP -> null
      else -> ApplicationInfo.getInstance().let {
        ContentVersion(it.majorVersion, it.minorVersion, eap = null, hash = null)
      }
    }
  }

  override fun getRequest(dataContext: DataContext?): HTMLEditorProvider.Request {
    val parameters = HashMap<String, String>()
    parameters["var"] = "embed"
    if (StartupUiUtil.isDarkTheme) {
      parameters["theme"] = "dark"
    }
    parameters["lang"] = getCurrentLanguageTag()
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
      LOG.error(e)
    }
    request.withQueryHandler(getHandler(dataContext))
    return request
  }

  override fun getActionWhiteList(): Set<String> {
    return actionWhiteList
  }

  override fun getVersion(): ContentVersion? {
    return parseUrl(url)
  }

  override suspend fun isAvailable(): Boolean {
    return checkConnectionAvailable()
  }

  private suspend fun checkConnectionAvailable(): Boolean {
    return withContext(Dispatchers.IO) {
      return@withContext try {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.setConnectTimeout(5000)
        connection.instanceFollowRedirects = false

        connection.connect()
        if (connection.responseCode != 200) {
          LOG.warn("WhatsNew page '$url' not available response code: ${connection.responseCode}")
          false
        }
        else {
          true
        }
      }
      catch (e: Exception) {
        LOG.warn("WhatsNew page connection error: '$e")
        false
      }
    }
  }
}

internal class WhatsNewVisionContent(page: WhatsNewInVisionContentProvider.Page) : WhatsNewContent() {
  companion object {
    private const val THEME_KEY = "\$__VISION_PAGE_SETTINGS_THEME__\$"
    private const val DARK_THEME = "dark"
    private const val LIGHT_THEME = "light"

    private const val LANG_KEY = "\$__VISION_PAGE_SETTINGS_LANGUAGE_CODE__\$"
  }

  val content: String
  private val contentHash: String
  private val myActionWhiteList: Set<String>

  init {
    var html = page.html
    if (page.publicVars.singleOrNull { it.value == THEME_KEY } != null) {
      html = html.replace(THEME_KEY, if (StartupUiUtil.isDarkTheme) { DARK_THEME } else { LIGHT_THEME })
    }
    if (page.publicVars.singleOrNull { it.value == LANG_KEY} != null) {
      html = html.replace(LANG_KEY, getCurrentLanguageTag())
    }
    content = html
    myActionWhiteList = page.actions.map { it.value }.toSet()
    contentHash = DigestUtil.sha1Hex(page.html)
  }

  override fun getRequest(dataContext: DataContext?): HTMLEditorProvider.Request {
    val request = html(content)
    request.withQueryHandler(getHandler(dataContext))
    return request
  }

  override fun getActionWhiteList(): Set<String> {
    return myActionWhiteList
  }

  override fun getVersion(): ContentVersion {
    val buildNumber = ApplicationInfo.getInstance().getBuild()
    return ContentVersion(
      ApplicationInfo.getInstance().majorVersion,
      ApplicationInfo.getInstance().minorVersion,
      if (buildNumber.components.size > 2) buildNumber.components[1] else buildNumber.components.last(),
      contentHash
    )
  }

  override suspend fun isAvailable(): Boolean {
    return true
  }
}

fun getCurrentLanguageTag(): String {
  return LocalizationStateService.getInstance()?.getSelectedLocale()?.lowercase() ?: run {
    logger.error("Cannot get a LocalizationStateService instance. Default to en-us locale.")
    "en-us"
  }
}

private val logger = logger<WhatsNewContent>()
