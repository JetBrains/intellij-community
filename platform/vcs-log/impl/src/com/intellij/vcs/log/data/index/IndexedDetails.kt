// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.LoadingDetailsImpl
import com.intellij.vcs.log.data.VcsLogStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IndexedDetails(private val dataGetter: IndexDataGetter,
                     storage: VcsLogStorage,
                     private val commitIndex: Int,
                     loadingTaskIndex: Long = 0) : LoadingDetailsImpl(storage, commitIndex, loadingTaskIndex) {
  private val _parents by lazy { dataGetter.getParents(commitIndex) }
  private val _author by lazy { dataGetter.getAuthor(commitIndex) }
  private val _committer by lazy { dataGetter.getCommitter(commitIndex) }
  private val _authorTime by lazy { dataGetter.getAuthorTime(commitIndex) }
  private val _commitTime by lazy { dataGetter.getCommitTime(commitIndex) }
  private val _fullMessage by lazy { dataGetter.getFullMessage(commitIndex) }

  override fun getFullMessage(): String {
    return _fullMessage ?: super.getFullMessage()
  }

  override fun getSubject(): String {
    return _fullMessage?.let { getSubject(it) } ?: super.getSubject()
  }

  override fun getParents(): List<Hash> {
    return _parents ?: super.getParents()
  }

  override fun getAuthor(): VcsUser {
    return _author ?: super.getAuthor()
  }

  override fun getCommitter(): VcsUser {
    return _committer ?: super.getCommitter()
  }

  override fun getAuthorTime(): Long {
    return _authorTime ?: super.getAuthorTime()
  }

  override fun getCommitTime(): Long {
    return _commitTime ?: super.getCommitTime()
  }

  companion object {
    @JvmStatic
    @NlsSafe
    fun getSubject(fullMessage: String): String {
      val subjectEnd = fullMessage.indexOf("\n\n")
      return if (subjectEnd > 0) fullMessage.substring(0, subjectEnd).replace("\n", " ") else fullMessage.replace("\n", " ")
    }

    @JvmStatic
    fun createMetadata(commitIndex: Int,
                       dataGetter: IndexDataGetter,
                       storage: VcsLogStorage,
                       factory: VcsLogObjectsFactory): VcsCommitMetadata? {
      val commitId = storage.getCommitId(commitIndex) ?: return null
      val parents = dataGetter.getParents(commitIndex) ?: return null
      val author = dataGetter.getAuthor(commitIndex) ?: return null
      val committer = dataGetter.getCommitter(commitIndex) ?: return null
      val authorTime = dataGetter.getAuthorTime(commitIndex) ?: return null
      val commitTime = dataGetter.getCommitTime(commitIndex) ?: return null
      val fullMessage = dataGetter.getFullMessage(commitIndex) ?: return null

      return factory.createCommitMetadata(commitId.hash, parents, commitTime, commitId.root, getSubject(fullMessage), author.name,
                                          author.email, fullMessage, committer.name, committer.email, authorTime)
    }
  }
}