// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.EventListener

@ApiStatus.Internal
interface VcsLogBookmarkSupport {
  fun getRefs(project: Project, hash: Hash, root: VirtualFile): List<VcsBookmarkRef>

  companion object {
    private val EP = ExtensionPointName.create<VcsLogBookmarkSupport>("com.intellij.vcs.log.bookmarkSupport")

    @JvmStatic
    fun getBookmarkRefs(project: Project, hash: Hash, root: VirtualFile): List<VcsBookmarkRef> {
      return EP.extensionList.flatMap { it.getRefs(project, hash, root) }
    }
  }
}

@ApiStatus.Internal
class VcsBookmarkRef(val bookmark: Bookmark, val type: BookmarkType, val text: @Nls String)

@ApiStatus.Internal
interface VcsLogBookmarksListener : EventListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<VcsLogBookmarksListener> = Topic(VcsLogBookmarksListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /**
   * Allows notifying VCS Log that the [VcsLogBookmarkSupport.getBookmarkRefs] might have changed.
   */
  fun logBookmarksChanged(): Unit = Unit
}
