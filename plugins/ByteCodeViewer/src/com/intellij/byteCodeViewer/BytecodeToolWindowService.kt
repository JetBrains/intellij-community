// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Ensures that the [ContentManagerListener] is registered only once
 * and properly unregistered when the project is disposed.
 */
@Service(Service.Level.PROJECT)
class BytecodeToolWindowService(private val project: Project) {
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

    val listener = object : ContentManagerListener {
      override fun contentAdded(event: ContentManagerEvent) {
        if (UISettings.getInstance().showDirectoryForNonUniqueFilenames) {
          deduplicateTabNames(toolWindow)
        }
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        if (UISettings.getInstance().showDirectoryForNonUniqueFilenames) {
          deduplicateTabNames(toolWindow)
        }
      }
    }
    contentManager.addContentManagerListener(listener)
    registeredListeners[toolWindow] = listener

    Disposer.register(toolWindow.disposable) {
      // The bytecode tool window cannot be "closed"  
      contentManager.removeContentManagerListener(listener)
      registeredListeners.remove(toolWindow)
    }
  }

  /**
   * Updates tab titles to avoid duplicate names by adding path information when necessary.
   */
  fun deduplicateTabNames(toolWindow: ToolWindow) {
    val titlesToContents = mutableMapOf<String, MutableList<Content>>()
    for (content in toolWindow.contentManager.contents) {
      val classFileName = content.getUserData(JAVA_CLASS_FILE)?.name
                          ?: throw IllegalStateException("Content entry has no JAVA_CLASS_FILE or it is null. Entry: $content")
      titlesToContents.getOrPut(classFileName) { mutableListOf() }.add(content)
    }
    for ((classFileName, contents) in titlesToContents) {
      if (contents.size == 1) {
        contents[0].displayName = classFileName
      }
      else if (contents.size > 1) {
        val paths = contents.map {
          it.getUserData(JAVA_CLASS_FILE) ?: throw IllegalStateException("No class file path for content entry $it")
        }

        val commonAncestor = VfsUtil.getCommonAncestor(paths) ?: continue

        for (i in contents.indices) {
          val content = contents[i]
          content.displayName = VfsUtil.getRelativePath(
            content.getUserData(JAVA_CLASS_FILE) ?: throw IllegalStateException("No class file path for content entry $content"),
            commonAncestor,
          )
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): BytecodeToolWindowService {
      return project.getService(BytecodeToolWindowService::class.java)
    }
  }
}