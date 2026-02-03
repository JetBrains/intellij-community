// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.LoggerFactory
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.workspaceModel.ide.impl.WorkspaceModelIdeBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection

private val LOG = logger<WorkspaceModelJsonDumpService>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class WorkspaceModelJsonDumpService(private val project: Project, private val coroutineScope: CoroutineScope) {
  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    prettyPrint = true
    explicitNulls = true
    prettyPrintIndent = "  "
  }
  
  private val logFileName = "workspace-model-dump.json"

  suspend fun getWorkspaceEntitiesAsJsonArray(): JsonArray {
    val snapshot = project.workspaceModel.currentSnapshot

    val wsmSerializers = WorkspaceModelSerializers()
    val rootEntities = snapshot.allUniqueEntities().rootEntitiesClasses().toList()

    return withBackgroundProgress(project, WorkspaceModelIdeBundle.message("progress.title.dumping.workspace.entities.json.to.clipboard")) {
      reportSequentialProgress(rootEntities.size) { reporter ->
        buildJsonArray {
          for (rootEntityClass in rootEntities) {
            reporter.itemStep(rootEntityClass.name)
            addJsonObject {
              put("rootEntityName", rootEntityClass.simpleName)
              val entities = snapshot.entities(rootEntityClass).toList()
              put("rootEntitiesCount", entities.size)
              putJsonArray("entities") {
                for ((i, entity) in entities.withIndex()) {
                  if (i % 100 == 0) ensureActive()
                  val jsonEntity = json.encodeToJsonElement(wsmSerializers[entity], entity)
                  add(jsonEntity)
                }
              }
            }
          }
        }
      }
    }
  }

  fun dumpWorkspaceEntitiesToClipboardAsJson() {
    coroutineScope.launch(Dispatchers.Default) {
      val jsonEntities = getWorkspaceEntitiesAsJsonArray()
      val serializedEntitiesString = json.encodeToString(jsonEntities)
      edtWriteAction {
        ensureActive()
        CopyPasteManager.getInstance().setContents(StringSelection(serializedEntitiesString))
      }
    }
  }

  fun dumpWorkspaceEntitiesToLogAsJson() {
    coroutineScope.launch(Dispatchers.Default) {
      val jsonEntities = getWorkspaceEntitiesAsJsonArray()
      val serializedEntitiesString = json.encodeToString(jsonEntities)
      LOG.info(serializedEntitiesString)
    }
  }

  fun dumpWorkspaceEntitiesToLogFileAsJson() {
    coroutineScope.launch(Dispatchers.IO) {
      val logDirectory2 = LoggerFactory.getLogFilePath().parent?.let {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)
      }?.takeIf { it.isDirectory }
      if (logDirectory2 == null) {
        val groupAndTitle = WorkspaceModelIdeBundle.message("notification.title.cannot.find.log.directory")
        val content = WorkspaceModelIdeBundle.message("notification.content.cannot.find.log.directory")
        Notifications.Bus.notify(Notification(groupAndTitle, groupAndTitle, content, NotificationType.INFORMATION))
        return@launch
      }
      val jsonEntities = getWorkspaceEntitiesAsJsonArray()
      val serializedEntitiesString = json.encodeToString(jsonEntities)
      val wsmDumpFile = edtWriteAction {
        logDirectory2.findChild(logFileName)?.delete(this)
        val wsmDumpFile = logDirectory2.createChildData(this, logFileName)
        wsmDumpFile.writeText(serializedEntitiesString)
        VfsUtil.markDirtyAndRefresh(true, false, false, wsmDumpFile)
        openDumpInEditor(wsmDumpFile)
        wsmDumpFile
      }
      LOG.info("Workspace model was dumped to ${wsmDumpFile.canonicalPath}")
    }
  }

  private fun openDumpInEditor(wsmDumpFile: VirtualFile) {
    val editors = FileEditorManager.getInstance(project).openFile(wsmDumpFile, true)
    if (editors.isNotEmpty() && editors.first() is TextEditor) {
      return
    }
    else {
      PsiNavigationSupport.getInstance().createNavigatable(project, wsmDumpFile, -1).navigate(true)
    }
  }
}
