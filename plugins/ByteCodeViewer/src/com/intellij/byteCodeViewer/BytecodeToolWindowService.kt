// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.filename.UniqueNameBuilder
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Ensures that the [ContentManagerListener] is registered only once
 * and properly unregistered when the project is disposed.
 */
@Service(Service.Level.PROJECT)
internal class BytecodeToolWindowService(private val project: Project) {
  private val registeredListeners = ConcurrentHashMap<ToolWindow, ContentManagerListener>()

  /**
   * Registers a ContentManagerListener for the BytecodeViewer tool window if not already registered.
   * The listener is responsible for updating tab titles when tabs are added or removed.
   *
   * If the listener for a specific instance of [ToolWindow] is already registered, this is a no-op.
   */
  fun ensureContentManagerListenerRegistered(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager

    // Check if the listener is already registered
    if (registeredListeners.containsKey(toolWindow)) {
      return
    }

    val messageBusConnection = project.messageBus.connect(toolWindow.disposable)
    messageBusConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      updateTabNames(toolWindow)
    })

    val listener = object : ContentManagerListener {
      override fun contentAdded(event: ContentManagerEvent) {
        if (UISettings.getInstance().showDirectoryForNonUniqueFilenames) {
          updateTabNames(toolWindow)
        }
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        if (UISettings.getInstance().showDirectoryForNonUniqueFilenames) {
          updateTabNames(toolWindow)
        }
      }
    }
    contentManager.addContentManagerListener(listener)
    registeredListeners[toolWindow] = listener

    Disposer.register(toolWindow.disposable) {
      // The bytecode tool window cannot be "closed"  
      contentManager.removeContentManagerListener(listener)
      registeredListeners.remove(toolWindow)
      messageBusConnection.disconnect()
    }
  }

  /**
   * Updates tab titles based on UI settings:
   * - If showDirectoryForNonUniqueFilenames is true: adds path information to disambiguate names
   * - If showDirectoryForNonUniqueFilenames is false: uses just the filename
   */
  fun updateTabNames(toolWindow: ToolWindow) {
    val javaClassFiles = toolWindow.contentManager.contents.map { content ->
      content.getUserData(JAVA_CLASS_FILE) ?: throw IllegalStateException("Content has no JAVA_CLASS_FILE or it is null. Content: $content")
    }

    // For a single tab, always use just the filename
    if (javaClassFiles.size == 1) {
      toolWindow.contentManager.contents[0].displayName = javaClassFiles[0].name
      return
    }

    // Check the UI setting to determine how to display tab names
    if (UISettings.getInstance().showDirectoryForNonUniqueFilenames) {
      // Add path information to disambiguate names
      val commonAncestor = VfsUtil.getCommonAncestor(javaClassFiles)?.path ?: return
      val uniqueNameBuilder = UniqueNameBuilder<String>(commonAncestor, "/")

      for (javaClassFile in javaClassFiles) {
        uniqueNameBuilder.addPath(javaClassFile.path, javaClassFile.path)
      }

      for (content in toolWindow.contentManager.contents) {
        val javaClassFile = content.getUserData(JAVA_CLASS_FILE)
                            ?: throw IllegalStateException("Content has no JAVA_CLASS_FILE or it is null. Content: $content")
        @NlsSafe val displayName = uniqueNameBuilder.getShortPath(javaClassFile.path)
                                   ?: throw IllegalStateException("Cannot get short path for ${javaClassFile.path}")
        content.displayName = displayName
      }
    } else {
      // Use just the filename for each tab
      for (content in toolWindow.contentManager.contents) {
        val javaClassFile = content.getUserData(JAVA_CLASS_FILE)
                            ?: throw IllegalStateException("Content has no JAVA_CLASS_FILE or it is null. Content: $content")
        content.displayName = javaClassFile.name
      }
    }
  }

  companion object {
    fun getInstance(project: Project): BytecodeToolWindowService {
      return project.getService(BytecodeToolWindowService::class.java)
    }
  }
}