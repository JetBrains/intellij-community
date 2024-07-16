// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.RepoStateException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import git4idea.config.GitVcsSettings
import git4idea.util.StringScanner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class GitTagHolder(val repository: GitRepository) {

  private val cs = repository.coroutineScope.childScope("GitTagHolder")
  private val repositoryFiles = repository.repositoryFiles

  private var tagsWithHashes: Map<GitTag, Hash> = mapOf()
  private var hashToTagCache: Map<String, GitTag> = mapOf()

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)
  private var isEnabled = false

  private val isLoadingFlow = MutableStateFlow(false)
  val isLoading: Boolean get() = isLoadingFlow.value

  fun updateEnabled() {
    isEnabled = GitVcsSettings.getInstance(repository.project).showTags()
    reload()
  }

  fun reload() {
    cs.launch {
      updateSemaphore.withPermit {
        updateState()
      }
    }
  }

  fun getTag(hash: String): GitTag? {
    return hashToTagCache[hash]
  }

  fun getTags(): Map<GitTag, Hash> {
    return tagsWithHashes
  }

  private suspend fun updateState() {
    if (isEnabled) {
      val tags = loadTagsForRepo()
      tagsWithHashes = tags.first
      hashToTagCache = tags.second
      BackgroundTaskUtil.syncPublisher(repository.project, GIT_TAGS_LOADED).tagsLoaded(repository)
    }
    else {
      tagsWithHashes = emptyMap()
      hashToTagCache = emptyMap()
    }
  }

  private suspend fun loadTagsForRepo(): Pair<MutableMap<GitTag, Hash>, Map<String, GitTag>> {
    isLoadingFlow.emit(true)
    val tags = mutableMapOf<GitTag, Hash>()
    val cache = mutableMapOf<String, GitTag>()
    try {
      readPackedTags(repositoryFiles.packedRefsPath, tags, cache)
      GitRefUtil.readFromRefsFiles(repositoryFiles.refsTagsFile,
                                   GitTag.REFS_TAGS_PREFIX,
                                   repositoryFiles) { tag, hash -> putValue(tag, hash, tags, cache) }
    }
    finally {
      isLoadingFlow.emit(false)
    }
    return tags to cache
  }

  private fun readPackedTags(myPackedRefsFile: File, tags: MutableMap<GitTag, Hash>, cache: MutableMap<String, GitTag>) {
    if (!myPackedRefsFile.exists()) {
      return
    }
    try {
      val content = DvcsUtil.tryLoadFile(myPackedRefsFile, CharsetToolkit.UTF8)

      val scanner = StringScanner(content)
      while (scanner.hasMoreData()) {
        val char = scanner.peek()
        if (char == '#') {
          scanner.nextLine()
          continue
        }
        if (char == '^') {
          // double annotation???
          scanner.nextLine()
          continue
        }

        val hash = scanner.spaceToken()
        val reference = scanner.line()
        if (reference.isBlank()) continue
        if (reference.startsWith(GitTag.REFS_TAGS_PREFIX)) {
          if (scanner.hasMoreData() && scanner.peek() == '^') {
            // annotated tag
            scanner.skipChars(1)
            val realHash = scanner.line()
            putValue(reference, realHash, tags, cache)
          }
          else {
            putValue(reference, hash, tags, cache)
          }
        }
      }
    }
    catch (e: RepoStateException) {
      LOG.info(e)
    }
  }

  private fun putValue(tagName: String, hash: String, tags: MutableMap<GitTag, Hash>, cache: MutableMap<String, GitTag>) {
    val gitTag = GitTag(tagName)
    cache[hash] = gitTag
    tags[gitTag] = HashImpl.build(hash)
  }

  companion object {
    private val LOG = logger<GitTagHolder>()
    val GIT_TAGS_LOADED: Topic<GitTagLoaderListener> = Topic.create("GitTags loaded", GitTagLoaderListener::class.java)
  }
}

fun interface GitTagLoaderListener : EventListener {
  fun tagsLoaded(repository: GitRepository)
}