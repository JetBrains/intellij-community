// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

abstract class ContentRootChangeListener(
  val skipFileChanges: Boolean,
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

    val removedList = collectChangedFiles(removed, event.storageAfter)
    val addedList = collectChangedFiles(added, event.storageBefore)
    if (removedList.isNotEmpty() || addedList.isNotEmpty()) {
      contentRootsChanged(removedList, addedList)
    }
  }

  // There might be a case when a content root with duplicating URL was registered or removed
  private fun collectChangedFiles(files: Sequence<VirtualFile>, storage: ImmutableEntityStorage): List<VirtualFile> {
    if (files.none()) return emptyList()

    val state = storage.contentRootPaths(skipFileChanges)
    return files.filterNot { it in state }.toList()
  }

  private fun ImmutableEntityStorage.contentRootPaths(skipFileChanges: Boolean): Set<VirtualFile> =
    entities(ContentRootEntity::class.java)
      .mapNotNull { it.url.virtualFile }
      .filter { it.isInLocalFileSystem }
      .let { entities -> if (skipFileChanges) entities.filter { it.isDirectory } else entities }
      .toSet()

  abstract fun contentRootsChanged(removed: List<VirtualFile>, added: List<VirtualFile>)
}
