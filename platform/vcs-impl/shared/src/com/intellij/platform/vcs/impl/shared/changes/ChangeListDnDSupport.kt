// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangeListDnDSupport {
  fun moveChangesTo(list: LocalChangeList, changes: List<Change>)
  fun addUnversionedFiles(list: LocalChangeList, unversionedFiles: List<FilePath>)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangeListDnDSupport = project.service<ChangeListDnDSupportImpl>()
  }
}

@Service(Service.Level.PROJECT)
private class ChangeListDnDSupportImpl(private val project: Project, private val cs: CoroutineScope) : ChangeListDnDSupport {
  override fun moveChangesTo(list: LocalChangeList, changes: List<Change>) {
    cs.launch {
      ChangeListsApi.getInstance().moveChanges(project.projectId(), changes.map(ChangeId::getId), list.id)
    }
  }

  override fun addUnversionedFiles(list: LocalChangeList, unversionedFiles: List<FilePath>) {
    cs.launch {
      ChangeListsApi.getInstance().addUnversionedFiles(project.projectId(), unversionedFiles.map(FilePathDto::toDto), list.id)
    }
  }
}
