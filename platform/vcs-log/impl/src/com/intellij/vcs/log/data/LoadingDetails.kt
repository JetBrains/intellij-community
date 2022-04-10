package com.intellij.vcs.log.data

import com.intellij.CommonBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.impl.VcsUserImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for [VcsShortCommitDetails] and [VcsFullCommitDetails] instances to indicate
 * that this is a placeholder object without any data.
 *
 * @see [VcsLog.getSelectedShortDetails]
 * @see [VcsLog.getSelectedDetails]
 */
interface LoadingDetails

@ApiStatus.Internal
open class LoadingDetailsImpl(storage: VcsLogStorage, commitIndex: Int, val loadingTaskIndex: Long) : VcsFullCommitDetails, LoadingDetails {
  private val commitId: CommitId by lazy(LazyThreadSafetyMode.PUBLICATION) { storage.getCommitId(commitIndex)!! }

  override fun getId(): Hash = commitId.hash
  override fun getRoot(): VirtualFile = commitId.root
  override fun getFullMessage(): String = ""
  override fun getSubject(): String = CommonBundle.getLoadingTreeNodeText()
  override fun getAuthor(): VcsUser = STUB_USER
  override fun getCommitter(): VcsUser = STUB_USER
  override fun getAuthorTime(): Long = -1
  override fun getCommitTime(): Long = -1
  override fun getParents(): List<Hash> = emptyList()
  override fun getTimestamp(): Long = -1
  override fun getChanges(): Collection<Change> = emptyList()
  override fun getChanges(parent: Int): Collection<Change> = emptyList()

  companion object {
    private val STUB_USER = VcsUserImpl("", "")
  }
}