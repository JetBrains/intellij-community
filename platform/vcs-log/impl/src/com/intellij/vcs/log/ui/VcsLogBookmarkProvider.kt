// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.ide.bookmark.*
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogNavigationUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*

private const val ROOT_KEY = "root"
private const val HASH_KEY = "hash"

internal class VcsLogBookmarkProvider(private val project: Project) : BookmarkProvider {
  override fun getWeight(): Int = 100
  override fun getProject(): Project = project

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    bookmark1 as VcsLogBookmark
    bookmark2 as VcsLogBookmark
    StringUtil.naturalCompare(bookmark1.root.path, bookmark2.root.path).let { if (it != 0) return it }
    StringUtil.naturalCompare(bookmark1.hash.asString(), bookmark2.hash.asString()).let { if (it != 0) return it }
    return 0
  }

  override fun createBookmark(map: MutableMap<String, String>): Bookmark? {
    val root = map[ROOT_KEY]?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return null
    val hash = map[HASH_KEY]?.let { HashImpl.build(it) } ?: return null
    return VcsLogBookmark(this, root, hash)
  }

  override fun createBookmark(context: Any?): Bookmark? = when (context) {
    is CommitId -> {
      VcsLogBookmark(this, context.root, context.hash)
    }
    else -> null
  }
}

internal class VcsLogBookmark(override val provider: VcsLogBookmarkProvider,
                              val root: VirtualFile,
                              val hash: Hash) : Bookmark {
  override val attributes: Map<String, String>
    get() = mapOf(ROOT_KEY to root.path,
                  HASH_KEY to hash.asString())

  override fun createNode(): BookmarkNode<*> = VcsLogBookmarkNode(provider.project, this)

  override fun canNavigate(): Boolean = true
  override fun canNavigateToSource(): Boolean = false
  override fun navigate(requestFocus: Boolean) {
    VcsLogNavigationUtil.jumpToRevisionAsync(provider.project, root, hash).whenComplete { result: Boolean?, error ->
      if (result != true) {
        val commitPresentation = VcsLogBundle.message("vcs.log.commit.prefix", hash)
        val message = VcsLogBundle.message("vcs.log.commit.not.found", commitPresentation)
        VcsNotifier.getInstance(provider.project).notifyWarning(VcsLogNotificationIdsHolder.COMMIT_NOT_FOUND, "", message);
      }
    }
  }

  override fun prepareDefaultDescription(): String? {
    return getDefaultBookmarkDescription(provider.project, root, hash)
  }

  override fun hashCode(): Int = Objects.hash(provider, root, hash)
  override fun equals(other: Any?): Boolean = other === this || other is VcsLogBookmark
                                              && other.provider == provider
                                              && other.root == root
                                              && other.hash == hash

  override fun toString(): String = "VcsLogBookmark(root=$root,hash=$hash)"
}

internal class VcsLogBookmarkNode(project: Project, bookmark: VcsLogBookmark) : BookmarkNode<VcsLogBookmark>(project, bookmark) {
  override fun getChildren(): List<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    val bookmark = value

    var description: String? = bookmarkDescription
    if (description == null && bookmarkGroup != null) {
      val defaultDescription = getDefaultBookmarkDescription(project, bookmark.root, bookmark.hash)
      if (defaultDescription != null) {
        description = defaultDescription
        bookmarkGroup?.setDescription(bookmark, description)
      }
    }

    presentation.setIcon(wrapIcon(null))
    if (description != null) {
      presentation.presentableText = description // configure speed search
      presentation.addText(description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      presentation.addText(" ${bookmark.hash.asString()}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    else {
      presentation.addText(bookmark.hash.asString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
  }
}

private fun getDefaultBookmarkDescription(project: Project, root: VirtualFile, hash: Hash): String? {
  val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return null
  val commitId = dataManager.getCommitIndex(hash, root)
  val details = dataManager.miniDetailsGetter.getCachedDataOrPlaceholder(commitId)
  if (details is LoadingDetails) return null
  return details.subject.ifBlank { VcsLogBundle.message("vcs.log.bookmark.description.empty.subject") }
}

@Service(Service.Level.PROJECT)
internal class VcsLogBookmarkReferenceProvider(val project: Project) {
  companion object {
    @JvmStatic
    fun getBookmarkRefs(project: Project, hash: Hash, root: VirtualFile): List<VcsBookmarkRef> {
      return project.service<VcsLogBookmarkReferenceProvider>().getBookmarkRefs(hash, root)
    }
  }

  fun getBookmarkRefs(hash: Hash, root: VirtualFile): List<VcsBookmarkRef> {
    val bookmarksManager = BookmarksManager.getInstance(project) ?: return emptyList()
    return bookmarksManager.bookmarks
      .filterIsInstance<VcsLogBookmark>()
      .filter { it.hash == hash && it.root == root }
      .map { logBookmark ->
        val type = bookmarksManager.getType(logBookmark) ?: BookmarkType.DEFAULT
        val text = when {
          type != BookmarkType.DEFAULT -> VcsLogBundle.message("vcs.log.bookmark.label.mnemonic", type.mnemonic)
          else -> VcsLogBundle.message("vcs.log.bookmark.label")
        }
        VcsBookmarkRef(logBookmark, type, text)
      }
  }
}

internal class VcsLogBookmarksManagerListener(val project: Project) : BookmarksListener {
  private fun updateBookmark(bookmark: Bookmark) {
    if (bookmark is VcsLogBookmark) {
      project.messageBus.syncPublisher(VcsLogBookmarksListener.TOPIC).logBookmarksChanged()
    }
  }

  override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) {
    updateBookmark(bookmark)
  }

  override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) {
    updateBookmark(bookmark)
  }

  override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) {
    updateBookmark(bookmark)
  }

  override fun bookmarkTypeChanged(bookmark: Bookmark) {
    updateBookmark(bookmark)
  }
}

@ApiStatus.Internal
class VcsBookmarkRef(val bookmark: Bookmark, val type: BookmarkType, val text: @Nls String)

internal interface VcsLogBookmarksListener : EventListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic(VcsLogBookmarksListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /**
   * Allows notifying VCS Log that the [VcsLogBookmarkReferenceProvider.getBookmarkRefs] might have changed.
   */
  fun logBookmarksChanged() = Unit
}
