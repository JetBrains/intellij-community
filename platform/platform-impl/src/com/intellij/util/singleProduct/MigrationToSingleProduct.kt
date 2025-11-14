// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.singleProduct

import com.intellij.idea.AppMode
import com.intellij.internal.InternalActionsBundle
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.openEditor
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request.Companion.html
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.ResourceHandler.ResourceResponse
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.HtmlVisionHelper
import com.intellij.util.HtmlVisionHelper.Companion.getMIMEType
import com.intellij.util.PlatformUtils
import com.intellij.util.Restarter
import com.intellij.util.system.OS
import com.intellij.util.vision.Container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.system.exitProcess

@ApiStatus.Internal
const val MIGRATION_FILE_MARKER: String = ".ce_migration_attempted"

@ApiStatus.Internal
fun migrateCommunityToSingleProductIfNeeded(args: List<String>) {
  if (
    !(PlatformUtils.isIdeaUltimate() || @Suppress("DEPRECATION") PlatformUtils.isPyCharmPro()) ||
    AppMode.isRemoteDevHost()
  ) return

  val currentDir = PathManager.getHomeDir().let { if (OS.CURRENT == OS.macOS) it.parent else it }
  val currentDirName = currentDir.name
  val newDirName = currentDirName.replace(" CE", "").replace(" Community Edition", "")
  if (newDirName == currentDirName) return

  // a marker file is used because standard storage is unavailable at this early startup stage (used later to trigger the Vision page)
  val migrationAttemptMarker = PathManager.getConfigDir().resolve(MIGRATION_FILE_MARKER)
  try {
    Files.writeString(migrationAttemptMarker, "", StandardOpenOption.CREATE_NEW)
  }
  catch (_: Exception) {
    return
  }

  if (System.getenv("TOOLBOX_VERSION") != null) return
  if (!(OS.CURRENT == OS.macOS && currentDirName.endsWith(".app"))) return

  val newDir = currentDir.resolveSibling(newDirName)
  if (Files.exists(newDir)) return

  try {
    InitialConfigImportState.writeOptionsForRestart(PathManager.getConfigDir())
  }
  catch (_: Exception) { }

  val commands = buildList {
    add(listOf("/bin/mv", "-n", currentDir.toString(), newDir.toString()))
    val vmOptionsFile = currentDir.resolveSibling("${currentDirName}.vmoptions")
    if (Files.exists(vmOptionsFile)) {
      val newVmOptionsFile = currentDir.resolveSibling("${newDirName}.vmoptions")
      add(listOf("/bin/mv", "-n", vmOptionsFile.toString(), newVmOptionsFile.toString()))
    }
    add(listOf(newDir.resolve("Contents/MacOS/${ApplicationNamesInfo.getInstance().scriptName}").toString()) + args)
  }
  Restarter.setMainAppArgs(args)  // fallback if the rename fails
  Restarter.scheduleRestart(false, *commands.toTypedArray())
  exitProcess(0)
}

private const val VISION_URL_MARKER = $$"$__VISION_PAGE_SETTINGS_MEDIA_BASE_PATH__$"
private const val VISION_RESOURCE_PACKAGE_PREFIX = "migration"

@ApiStatus.Internal
class MigrationStartupActivity : ProjectActivity {
  private var html: String? = null
  private var fileEditor: FileEditor? = null

  private fun getRequest(project: Project): HTMLEditorProvider.Request {
    val request = html(html ?: "")
    request.withQueryHandler(MigrationToSingleProductVisionQueryHandler(project))
    request.withResourceHandler(HtmlResourceHandler())
    return request
  }

  @OptIn(ExperimentalSerializationApi::class)
  override suspend fun execute(project: Project) {
    if (!PlatformUtils.isIdeaUltimate() && @Suppress("DEPRECATION") !PlatformUtils.isPyCharmPro() || AppMode.isRemoteDevHost()) {
      return
    }
    val migrationAttemptMarker = PathManager.getConfigDir().resolve(MIGRATION_FILE_MARKER)
    if (!Files.exists(migrationAttemptMarker)) {
      return
    }
    if (MigrationToSingleProductSettings.getInstance().state.afterMigrationDocumentWasShown) {
      return
    }
    MigrationToSingleProductSettings.getInstance().state.afterMigrationDocumentWasShown = true

    val json = Json { ignoreUnknownKeys = true }
    val ioStream = MigrationToSingleProductResourceProvider.getInstance().getVisionPage()
    val container = ioStream?.use { inputStream ->
      json.decodeFromStream<Container>(inputStream)
    } ?: error("Vision page not found")

    val page = container.entities.firstOrNull() ?: error("Vision document is empty")
    val publicVarsPattern = page.publicVars.distinctBy { it.value }.joinToString("|") { Regex.escape(it.value) }.toRegex()
    html = HtmlVisionHelper.processContent(page.html, publicVarsPattern)
    val request = getRequest(project)

    withContext(Dispatchers.EDT) {
      fileEditor = writeIntentReadAction {
        MigrateToSingleProductCollector.WELCOME_PAGE_SHOWN.log()
        openEditor(project, InternalActionsBundle.message("tab.welcome.to.single.product"), request)
      }
    }
  }

  private class HtmlResourceHandler : HTMLEditorProvider.ResourceHandler {
    override fun shouldInterceptRequest(request: HTMLEditorProvider.ResourceHandler.ResourceRequest): Boolean =
      request.uri.path.contains(VISION_URL_MARKER)

    override suspend fun handleResourceRequest(request: HTMLEditorProvider.ResourceHandler.ResourceRequest): ResourceResponse {
      val nameStartIndex = request.uri.path.indexOf(VISION_URL_MARKER) + VISION_URL_MARKER.length
      val resourceName = VISION_RESOURCE_PACKAGE_PREFIX + request.uri.path.substring(nameStartIndex)

      val resourceStream = MigrationToSingleProductResourceProvider.getInstance().javaClass.classLoader.getResourceAsStream(resourceName)
      if (resourceStream == null) {
        return ResourceResponse.NotFound
      }
      val mimeType = getMIMEType(request.uri.path.toNioPathOrNull()?.extension ?: "")
      val resource = object : HTMLEditorProvider.ResourceHandler.Resource {
        override val mimeType: String
          get() = mimeType

        override suspend fun getResourceStream(): InputStream = resourceStream
      }

      return ResourceResponse.HandleResource(resource)
    }
  }

  private inner class MigrationToSingleProductVisionQueryHandler(val project: Project) : HTMLEditorProvider.JsQueryHandler {
    override suspend fun query(id: Long, request: String): String {
      val editor = fileEditor ?: return ""
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.VIRTUAL_FILE, editor.file)
        .build()

      val action = service<ActionManager>().getAction(request)
      if (action != null) {
        val event = AnActionEvent.createEvent(action, dataContext, null, "", ActionUiKind.NONE, null)
        MigrateToSingleProductCollector.BUTTON_CLICKED.log()
        action.actionPerformed(event)
      }
      return ""
    }
  }
}

@ApiStatus.Internal
private object MigrateToSingleProductCollector : CounterUsagesCollector() {
  private val GROUP = @Suppress("DEPRECATION") EventLogGroup("migrate.to.single.product", 1)

  @JvmField
  val WELCOME_PAGE_SHOWN: EventId = GROUP.registerEvent("vision.page.shown", "How many times button on welcome vision page was shown after patch update to SID")

  @JvmField
  val BUTTON_CLICKED: EventId = GROUP.registerEvent("vision.button.clicked", "How many times button on welcome vision page was clicked")

  override fun getGroup(): EventLogGroup = GROUP
}

@ApiStatus.Internal
interface MigrationToSingleProductResourceProvider {
  suspend fun getVisionPage(): InputStream?

  companion object {
    @JvmStatic
    fun getInstance(): MigrationToSingleProductResourceProvider = service()  // NB: registered only in IU and PY; check before use
  }
}
