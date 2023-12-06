// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.LoadingDetailsImpl
import com.intellij.vcs.log.data.VcsLogStorage
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.util.*

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
    fun createMetadata(commitIndexes: Set<Int>,
                       dataGetter: IndexDataGetter,
                       storage: VcsLogStorage,
                       factory: VcsLogObjectsFactory): Int2ObjectMap<VcsCommitMetadata> {

      val commitIds = storage.getCommitIds(commitIndexes)
      if (commitIds.isEmpty()) return Int2ObjectMaps.emptyMap()

      val authors = dataGetter.getAuthor(commitIndexes) ?: return Int2ObjectMaps.emptyMap()
      val committers = dataGetter.getCommitter(commitIndexes)
      val messages = dataGetter.getFullMessage(commitIndexes) ?: return Int2ObjectMaps.emptyMap()
      val parents = dataGetter.getParents(commitIndexes) ?: return Int2ObjectMaps.emptyMap()
      val authorTimes = dataGetter.getAuthorTime(commitIndexes) ?: return Int2ObjectMaps.emptyMap()
      val commitTimes = dataGetter.getCommitTime(commitIndexes) ?: return Int2ObjectMaps.emptyMap()

      val result = Int2ObjectOpenHashMap<VcsCommitMetadata>()

      for (commitIndex in commitIndexes) {
        val commitId = commitIds[commitIndex]
        val parent = parents[commitIndex]
        val author = authors[commitIndex]
        val committer = committers[commitIndex] ?: author
        val authorTime = authorTimes[commitIndex]
        val commitTime = commitTimes[commitIndex]
        val fullMessage = messages[commitIndex]

        if (commitId != null && parent != null && author != null && committer != null && commitTime != null && authorTime != null && fullMessage != null) {
          result.put(commitIndex, factory.createCommitMetadata(commitId.hash, parent, commitTime, commitId.root, getSubject(fullMessage),
                                                               author.name, author.email, fullMessage, committer.name, committer.email,
                                                               authorTime))
        }
      }

      return result
    }

    @JvmStatic
    fun createMetadata(commitIndex: Int,
                       dataGetter: IndexDataGetter,
                       storage: VcsLogStorage,
                       factory: VcsLogObjectsFactory): VcsCommitMetadata? {
      return createMetadata(Collections.singleton(commitIndex), dataGetter, storage, factory)
        .getOrDefault(commitIndex, null)
    }
  }
}
