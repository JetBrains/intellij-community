// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile
import java.util.*

internal class GHPRFilesManagerImpl(private val project: Project,
                                    private val repository: GHRepositoryCoordinates) : GHPRFilesManager {

  // current time should be enough to distinguish the manager between launches
  private val id = System.currentTimeMillis().toString()

  private val filesEventDispatcher = EventDispatcher.create(FileListener::class.java)

  private val files = ContainerUtil.createWeakMap<GHPRIdentifier, GHPRVirtualFile>()

  override fun createAndOpenFile(pullRequest: GHPRIdentifier, requestFocus: Boolean) {
    files.getOrPut(SimpleGHPRIdentifier(pullRequest)) {
      GHPRVirtualFile(id, project, repository, pullRequest)
    }.let {
      filesEventDispatcher.multicaster.onBeforeFileOpened(it)
      FileEditorManager.getInstance(project).openFile(it, requestFocus)
    }
  }

  override fun findFile(pullRequest: GHPRIdentifier): GHPRVirtualFile? = files[SimpleGHPRIdentifier(pullRequest)]

  override fun updateFilePresentation(details: GHPullRequestShort) {
    val file = findFile(details)
    if (file != null) {
      file.details = details
      // TODO: clear cached icons at com.intellij.util.IconUtil.getIcon
      // TODO: update tooltip
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }

  override fun addBeforeFileOpenedListener(disposable: Disposable, listener: (file: GHPRVirtualFile) -> Unit) {
    filesEventDispatcher.addListener(object : FileListener {
      override fun onBeforeFileOpened(file: GHPRVirtualFile) = listener(file)
    }, disposable)
  }

  override fun dispose() {
    for ((_, file) in files) {
      FileEditorManager.getInstance(project).closeFile(file)
      file.valid = false
    }
  }

  private interface FileListener : EventListener {
    fun onBeforeFileOpened(file: GHPRVirtualFile)
  }
}
