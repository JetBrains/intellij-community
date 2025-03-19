// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

private class LibraryDependentToolWindowManager : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  @OptIn(FlowPreview::class)
  override suspend fun execute(project: Project) {
    awaitToolwindowManager(project)

    coroutineScope {
      val checkRequests = MutableSharedFlow<String?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      checkRequests.emit(null)

      readActionBlocking {
        val connection = project.messageBus.connect(this)
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            check(checkRequests.tryEmit(null))
          }
        })
        connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                             AdditionalLibraryRootsListener { _: String?, _: Collection<VirtualFile?>?, _: Collection<VirtualFile?>?, _: String? ->
                               check(checkRequests.tryEmit(null))
                             })
        LibraryDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(
          object : ExtensionPointListener<LibraryDependentToolWindow> {
            override fun extensionAdded(extension: LibraryDependentToolWindow, pluginDescriptor: PluginDescriptor) {
              check(checkRequests.tryEmit(null))
            }

            override fun extensionRemoved(extension: LibraryDependentToolWindow, pluginDescriptor: PluginDescriptor) {
              ToolWindowManager.getInstance(project).getToolWindow(extension.id)?.remove()
            }
          }, project)
      }

      checkRequests
        .debounce(100.milliseconds)
        .collectLatest {
          checkToolWindowStatuses(project = project, extensionId = it)
        }
    }
  }

  private suspend fun awaitToolwindowManager(project: Project) {
    return suspendCancellableCoroutine { continuation ->
      ToolWindowManager.getInstance(project).invokeLater {
        continuation.resumeWith(Result.success(Unit))
      }
    }
  }
}

@JvmRecord
private data class LibraryWindowsState(
  @JvmField val project: Project,
  @JvmField val extensions: List<LibraryDependentToolWindow>,
  @JvmField val existing: Set<LibraryDependentToolWindow>,
)

private suspend fun checkToolWindowStatuses(project: Project, extensionId: String? = null) {
  var extensions = LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensionList
  if (extensions.isEmpty()) {
    return
  }

  val state = smartReadAction(project) {
    if (extensionId != null) {
      extensions = extensions.filter { it.id == extensionId }
    }
    val existing = extensions.filterTo(HashSet()) { toolWindow ->
      val helper = toolWindow.librarySearchHelper
      helper != null && helper.isLibraryExists(project)
    }

    if (extensions.isEmpty()) {
      null
    }
    else {
      LibraryWindowsState(project = project, extensions = extensions, existing = existing)
    }
  } ?: return

  withContext(Dispatchers.EDT) {
    applyWindowsState(state)
  }
}

private suspend fun applyWindowsState(state: LibraryWindowsState) {
  val toolWindowManager = ToolWindowManager.getInstance(state.project) as ToolWindowManagerImpl
  for (libraryToolWindow in state.extensions) {
    var toolWindow = toolWindowManager.getToolWindow(libraryToolWindow.id)
    if (state.existing.contains(libraryToolWindow)) {
      if (toolWindow == null) {
        toolWindowManager.initToolWindow(libraryToolWindow, libraryToolWindow.pluginDescriptor)
      }
    }
    else {
      toolWindow?.remove()
    }
  }
}