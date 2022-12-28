// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.util.concurrency.SequentialTaskExecutor

private class LibraryDependentToolWindowManager : StartupActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun runActivity(project: Project) {
    val rootListener = object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        checkToolWindowStatuses(project)
      }
    }

    checkToolWindowStatuses(project)
    val connection = project.messageBus.simpleConnect()
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, rootListener)
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                         AdditionalLibraryRootsListener { _: String?, _: Collection<VirtualFile?>?, _: Collection<VirtualFile?>?, _: String? ->
                           checkToolWindowStatuses(project)
                         })
    LibraryDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(object : ExtensionPointListener<LibraryDependentToolWindow> {
      override fun extensionAdded(extension: LibraryDependentToolWindow, pluginDescriptor: PluginDescriptor) {
        checkToolWindowStatuses(project, extension.id)
      }

      override fun extensionRemoved(extension: LibraryDependentToolWindow, pluginDescriptor: PluginDescriptor) {
        ToolWindowManager.getInstance(project).getToolWindow(extension.id)?.remove()
      }
    }, project)
  }
}

@JvmRecord
private data class LibraryWindowsState(@JvmField val project: Project,
                                       @JvmField val extensions: List<LibraryDependentToolWindow>,
                                       @JvmField val existing: Set<LibraryDependentToolWindow>)

private fun checkToolWindowStatuses(project: Project, extensionId: String? = null) {
  val currentModalityState = ModalityState.current()
  ReadAction.nonBlocking<LibraryWindowsState> {
    var extensions = LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensionList
    if (extensionId != null) {
      extensions = extensions.filter { it.id == extensionId }
    }
    val existing = extensions.filterTo(HashSet()) { toolWindow ->
      val helper = toolWindow.librarySearchHelper
      helper != null && helper.isLibraryExists(project)
    }
    LibraryWindowsState(project = project, extensions = extensions, existing = existing)
  }
    .inSmartMode(project)
    .coalesceBy(project)
    .finishOnUiThread(currentModalityState, ::applyWindowsState)
    .submit(ourExecutor)
}

private val ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LibraryDependentToolWindowManager")

private fun applyWindowsState(state: LibraryWindowsState) {
  val toolWindowManager = ToolWindowManagerEx.getInstanceEx(state.project)
  for (libraryToolWindow in state.extensions) {
    var toolWindow = toolWindowManager.getToolWindow(libraryToolWindow.id)
    if (state.existing.contains(libraryToolWindow)) {
      if (toolWindow == null) {
        toolWindowManager.initToolWindow(libraryToolWindow)
        if (!libraryToolWindow.showOnStripeByDefault) {
          toolWindow = toolWindowManager.getToolWindow(libraryToolWindow.id)
          if (toolWindow != null) {
            val windowInfo = toolWindowManager.getLayout().getInfo(libraryToolWindow.id)
            if (windowInfo != null && !windowInfo.isFromPersistentSettings) {
              toolWindow.isShowStripeButton = false
            }
          }
        }
      }
    }
    else {
      toolWindow?.remove()
    }
  }
}