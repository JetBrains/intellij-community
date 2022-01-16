package com.intellij.vcs.log.data

import com.intellij.CommonBundle
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl

interface LoadingDetails

/**
 * Fake [com.intellij.vcs.log.impl.VcsCommitMetadataImpl] implementation that is used to indicate that details are not ready for the moment,
 * they are being retrieved from the VCS.
 *
 * @author Kirill Likhodedov
 */
open class LoadingDetailsImpl(private val commitIdComputable: Computable<out CommitId>, val loadingTaskIndex: Long) : VcsFullCommitDetails, LoadingDetails {
  private val commitId: CommitId by lazy(LazyThreadSafetyMode.PUBLICATION) { commitIdComputable.compute() }

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