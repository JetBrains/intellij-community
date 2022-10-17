// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

abstract class ContentRootChangeListener(
  val skipFileChanges: Boolean
) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    val removedUrls = mutableSetOf<VirtualFileUrl>()
    val addedUrls = mutableSetOf<VirtualFileUrl>()

    val changes = event.getChanges(ContentRootEntity::class.java)
    for (change in changes) {
      val removedUrl = change.oldEntity?.url
      val addedUrl = change.newEntity?.url
      if (removedUrl != addedUrl) {
        if (removedUrl != null) removedUrls += removedUrl
        if (addedUrl != null) addedUrls += addedUrl
      }
    }

    var removed = removedUrls
      .asSequence()
      .filter { !addedUrls.contains(it) } // Do not process 'modifications' of any kind (ex: excluded root changes).
      .mapNotNull { it.virtualFile }
    var added = addedUrls
      .asSequence()
      .filter { !removedUrls.contains(it) }
      .mapNotNull { it.virtualFile }

    if (skipFileChanges) {
      // Filter-out 'file as content-roots'. Ex: Rider-project model.
      removed = removed.filter { it.isDirectory }
      added = added.filter { it.isDirectory }
    }

    val removedList = removed.toList()
    val addedList = added.toList()
    if (removedList.isNotEmpty() || addedList.isNotEmpty()) {
      contentRootsChanged(removedList, addedList)
    }
  }

  abstract fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>)
}
