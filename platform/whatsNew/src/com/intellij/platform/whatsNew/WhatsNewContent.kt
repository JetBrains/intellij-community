// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettingsService
import com.intellij.ide.whatsNew.DefaultWhatsNewQueryHandler
import com.intellij.ide.whatsNew.WhatsNewUtil
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.openEditorAsync
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.JsQueryHandler
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request.Companion.html
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Version
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.whatsNew.collectors.WhatsNewCounterUsageCollector
import com.intellij.platform.whatsNew.reaction.FUSReactionChecker
import com.intellij.platform.whatsNew.reaction.ReactionsPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.application
import com.intellij.util.io.DigestUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal abstract class WhatsNewContent {
  companion object {
    suspend fun getWhatsNewContent(): WhatsNewContent? {
      return if (WhatsNewInVisionContentProvider.getInstance().isAvailable() &&
                 // JCEF is not accessible for screen readers (IJPL-59438), so also need to open the page in the browser
                 !ScreenReader.isActive()) {
        val provider = WhatsNewInVisionContentProvider.getInstance()
        WhatsNewVisionContent(provider, provider.getContent().entities.first())
      }
      else {
        ExternalProductResourceUrls.getInstance().whatIsNewPageUrl?.toDecodedForm()?.let { WhatsNewUrlContent(it) }
      }
    }
  }

  // Year and release have to be strings, because this is the ApplicationInfo.xml format.
  // Remember that "release" might be a string like "2.1".
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
      }
      else {
        return "$year-$release-$eap"
      }
    }

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

  abstract suspend fun show(
    project: Project,
    dataContext: DataContext?,
    triggeredByUser: Boolean,
    reactionChecker: FUSReactionChecker,
  )

  abstract fun getVersion(): ContentVersion?
  abstract suspend fun isAvailable(): Boolean
}

internal class WhatsNewUrlContent(val url: String) : WhatsNewContent() {
  companion object {
    val LOG = logger<WhatsNewUrlContent>()
  }

  override fun getVersion(): ContentVersion? = null

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
        if (connection.responseCode >= 400) {
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

  override suspend fun show(project: Project, dataContext: DataContext?, triggeredByUser: Boolean, reactionChecker: FUSReactionChecker) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url))
  }
}

internal class WhatsNewVisionContent(val contentProvider: WhatsNewInVisionContentProvider, page: WhatsNewInVisionContentProvider.Page) :
  WhatsNewContent() {
  companion object {
    const val WHATS_NEW_VISION_SCHEME = "whatsnew-vision"
    const val LOCALHOST = "localhost"

    private const val THEME_KEY = $$"$__VISION_PAGE_SETTINGS_THEME__$"
    private const val DARK_THEME = "dark"
    private const val LIGHT_THEME = "light"

    private const val LANG_KEY = $$"$__VISION_PAGE_SETTINGS_LANGUAGE_CODE__$"
    private const val ZOOM_KEY = $$"$__VISION_PAGE_SETTINGS_ZOOM_IN_ACTION__$"
    private const val GIF_KEY = $$"$__VISION_PAGE_SETTINGS_GIF_PLAYER_ACTION__$"
    private const val MEDIA_BASE_PATH_KEY = $$"$__VISION_PAGE_SETTINGS_MEDIA_BASE_PATH__$"
    private const val AIR_BUTTON_ALT_TEXT_KEY = $$"$__VISION_PAGE_SETTINGS_IS_AIR_BUTTON_ALT_TEXT_ENABLED__$"
    private const val IS_CHINESE_KEY = $$"$__VISION_PAGE_SETTINGS_IS_CHINESE__$"

    private const val ZOOM_VALUE = "whatsnew.vision.zoom"
    private const val GIF_VALUE = "whatsnew.vision.gif"
    private const val MEDIA_BASE_PATH_VALUE = "$WHATS_NEW_VISION_SCHEME://$LOCALHOST"
  }

  val content: String
  private val contentHash: String
  private val myActionWhiteList: Set<String>
  private val visionActionIds = setOf(GIF_VALUE, ZOOM_VALUE)
  internal val multipageIds: List<String>

  init {
    var html = page.html
    val pattern = page.publicVars.distinctBy { it.value }
      .joinToString("|") { Regex.escape(it.value) }.toRegex()
    html = html.replace(pattern) {
      when (it.value) {
        THEME_KEY -> if (StartupUiUtil.isDarkTheme) {
          DARK_THEME
        }
        else {
          LIGHT_THEME
        }
        LANG_KEY -> getCurrentLanguageTag()
        ZOOM_KEY -> ZOOM_VALUE
        GIF_KEY -> GIF_VALUE
        MEDIA_BASE_PATH_KEY -> MEDIA_BASE_PATH_VALUE
        else -> it.value
      }
    }
    content = html
    myActionWhiteList = page.actions.map { it.value }.toSet()
    multipageIds = page.multipageIds
    contentHash = DigestUtil.sha1Hex(page.html)
  }

  /**
   * Returns [content] with the variables replaced that can change between two openings of the page.
   * On the welcome screen, the Vision page shows the alternative label and action of the Air button.
   * The China flag marks the IDE region, not the IDE language.
   */
  internal fun getContent(isWelcomeScreenProject: Boolean, isChinaRegion: Boolean): String =
    content
      .replace(AIR_BUTTON_ALT_TEXT_KEY, isWelcomeScreenProject.toString())
      .replace(IS_CHINESE_KEY, isChinaRegion.toString())

  @OptIn(DelicateCoroutinesApi::class)
  private fun getRequestHandler(dataContext: DataContext?): HTMLEditorProvider.ResourceHandler? {
    if (dataContext == null) return logger.error("dataContext is null").let { null }

    return WhatsNewRequestHandler(contentProvider)
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

  override suspend fun isAvailable(): Boolean = true

  private fun getQueryHandler(dataContext: DataContext?, project: Project): JsQueryHandler? {
    dataContext ?: return null

    return object : JsQueryHandler {
      private val delegate = DefaultWhatsNewQueryHandler(project)

      override suspend fun query(id: Long, request: String): String {
        val contains = myActionWhiteList.contains(request)
        if (!contains) {
          if (visionActionIds.contains(request)) {
            WhatsNewCounterUsageCollector.visionActionPerformed(dataContext.project, request)
            logger.trace { "EapWhatsNew action $request performed" }
            return "true"
          }
          logger.trace { "EapWhatsNew action $request not allowed" }
          WhatsNewCounterUsageCollector.actionNotAllowed(dataContext.project, request)
          return "false"
        }

        if (request.isNotEmpty()) {
          val action = service<ActionManager>().getAction(request)
          if (action != null) {
            withContext(Dispatchers.EDT) {
              action.actionPerformed(
                AnActionEvent.createEvent(action, dataContext, /*presentation =*/ null, WhatsNewUtil.PLACE, ActionUiKind.NONE, /*event =*/ null)
              )
              logger.trace { "EapWhatsNew action $request performed" }
              WhatsNewCounterUsageCollector.actionPerformed(dataContext.project, request)
            }
            return "true"
          }

          val result = delegate.query(id, request)
          if (result != "true") {
            logger.trace { "EapWhatsNew action $request not found" }
            WhatsNewCounterUsageCollector.actionNotFound(dataContext.project, request)
          }
          return result
        }

        return "false"
      }
    }
  }

  override suspend fun show(project: Project, dataContext: DataContext?, triggeredByUser: Boolean, reactionChecker: FUSReactionChecker) {
    if (!JBCefApp.isSupported()) {
      logger.error("EapWhatsNew: can't be shown. JBCefApp isn't supported")
    }

    val title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().fullProductName)
    val isChinaRegion = RegionSettingsService.getInstanceAsync().getCurrentRegion() == Region.CHINA
    withContext(Dispatchers.EDT) {
      logger.info("Opening What's New in editor.")
      val disposable = Disposer.newDisposable(project)
      val startPageId = WhatsNewMultipageStartIdProvider.getInstance(project).getId()
      val request = getRequest(startPageId, dataContext, project, isChinaRegion)
      val editor = openEditorAsync(project, title, request)

      editor?.let {
        project.serviceAsync<FileEditorManager>().addTopComponent(it, ReactionsPanel.createPanel(WhatsNewUtil.PLACE, reactionChecker))
        val startTime = System.currentTimeMillis()
        WhatsNewCounterUsageCollector.openedPerformed(project, startPageId, triggeredByUser)

        if (WhatsNewContentVersionChecker.saveLastShownContent(this@WhatsNewVisionContent)) {
          reactionChecker.clearLikenessState()
        }

        val busConnection = application.messageBus.connect(disposable)
        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            if (it.file == file) {
              val durationSeconds = (System.currentTimeMillis() - startTime) / 1000
              WhatsNewCounterUsageCollector.closedPerformed(project, startPageId, durationSeconds)
              Disposer.dispose(disposable)
            }
          }
        })
      } ?: Disposer.dispose(disposable)
    }
  }

  private fun getRequest(startPageId: String, dataContext: DataContext?, project: Project, isChinaRegion: Boolean): HTMLEditorProvider.Request {
    val pageContent = getContent(WelcomeScreenProjectProvider.isWelcomeScreenProject(project), isChinaRegion)
    // One-page case
    val request = if (startPageId == WhatsNewInVisionContentProvider.DEFAULT_MULTIPAGE_ID || multipageIds.size == 1) {
      html(pageContent)
    }
    // Multipage case
    else {
      html(pageContent, "file:///jbcefbrowser#/$startPageId").withOnUrlChanged { oldUrl, newUrl ->
        WhatsNewCounterUsageCollector.multipageIdChanged(dataContext?.project, oldUrl?.substringAfter("#/"), newUrl.substringAfter("#/"))
      }
    }
    return request
      .withQueryHandler(getQueryHandler(dataContext, project))
      .withResourceHandler(getRequestHandler(dataContext))
  }
}

fun getCurrentLanguageTag(): String {
  return LocalizationStateService.getInstance()?.getSelectedLocale()?.lowercase() ?: run {
    logger.error("Cannot get a LocalizationStateService instance. Default to en-us locale.")
    "en-us"
  }
}

private val DataContext.project: Project?
  get() = CommonDataKeys.PROJECT.getData(this)

private val logger = logger<WhatsNewContent>()
