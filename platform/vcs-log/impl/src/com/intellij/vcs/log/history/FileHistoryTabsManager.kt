// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.TabGroupId
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlinx.coroutines.CoroutineScope
import java.util.function.Function

@Service(Service.Level.PROJECT)
internal class FileHistoryTabsManager(val project: Project, coroutineScope: CoroutineScope) {
  private val tabGroupId = TabGroupId("History", VcsBundle.messagePointer("file.history.tab.name"), false)
  private val historyTabs = mutableListOf<FileHistoryTab>()
  private var isLogDisposing = false

  init {
    project.messageBus.connect(coroutineScope).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, object : VcsProjectLog.ProjectLogListener {
      override fun logCreated(manager: VcsLogManager) {
        isLogDisposing = false
        ToolWindowManager.getInstance(project).invokeLater {
          if (manager.isDisposed) return@invokeLater
          historyTabs.removeIf { !manager.dataManager.logProviders.keys.contains(it.root) }
          historyTabs.forEach { (path, root, hash) ->
            doOpenFileHistoryTab(manager, path, root, hash, false)
          }
        }
      }

      override fun logDisposed(manager: VcsLogManager) {
        isLogDisposing = true
      }
    })
  }

  @RequiresEdt
  fun openFileHistoryTab(logManager: VcsLogManager, path: FilePath, root: VirtualFile, hash: Hash?): FileHistoryUi {
    historyTabs.add(FileHistoryTab(path, root, hash))
    return doOpenFileHistoryTab(logManager, path, root, hash, true)
  }

  private fun doOpenFileHistoryTab(logManager: VcsLogManager, path: FilePath, root: VirtualFile, hash: Hash?, focus: Boolean): FileHistoryUi {
    val suffix = if (hash != null) " (" + hash.toShortString() + ")" else ""
    val fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, tabGroupId, Function { path.name + suffix },
                                                     FileHistoryUiFactory(path, root, hash), focus)
    Disposer.register(fileHistoryUi, Disposable {
      if (!isLogDisposing) historyTabs.remove(FileHistoryTab(path, root, hash))
    })
    return fileHistoryUi
  }

  private data class FileHistoryTab(val path: FilePath, val root: VirtualFile, val hash: Hash?)
}