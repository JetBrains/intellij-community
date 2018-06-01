/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data.index

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.VcsLogStorage

class IndexedDetails(private val dataGetter: IndexDataGetter,
                     storage: VcsLogStorage,
                     private val commitIndex: Int,
                     loadingTaskIndex: Long) : LoadingDetails({ storage.getCommitId(commitIndex) }, loadingTaskIndex) {
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