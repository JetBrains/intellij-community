// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.fetch.GitFetchHandler
import git4idea.repo.GitRepositoryTagsHolder.Companion.TAGS_UPDATED
import git4idea.util.StringScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

internal class GitRepositoryTagsHolderImpl(
  private val repository: GitRepository,
) : GitRepositoryTagsHolder {
  private val cs = repository.coroutineScope.childScope("GitRepositoryTagsHolder")

  private val _state = MutableStateFlow<GitRepositoryTagsState>(GitRepositoryTagsState.NotLoaded)
  override val state: StateFlow<GitRepositoryTagsState> = _state.asStateFlow()

  private val updateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    cs.launch(Dispatchers.IO) {
      updateRequests.collectLatest {
        _state.value = loadTagsFromGit()
      }
    }

    cs.launch {
      _state.collect { repository.project.messageBus.syncPublisher(TAGS_UPDATED).tagsUpdated(repository) }
    }
  }

  override fun reload() {
    updateRequests.tryEmit(Unit)
  }

  @VisibleForTesting
  fun loadTagsFromGit(): GitRepositoryTagsState {
    val handler = GitLineHandler(repository.project, repository.root, GitCommand.FOR_EACH_REF)
    handler.addParameters("refs/tags/**")
    handler.addParameters("--no-color")
    handler.addParameters("--format=%(refname)\t%(*objectname)\t%(objectname)")

    val listener = TagsLineListener()
    handler.addLineListener(listener)

    Git.getInstance().runCommand(handler).throwOnError()
    return listener.getTagsState()
  }

  @TestOnly
  @RequiresBackgroundThread
  fun updateForTests() {
    _state.value = loadTagsFromGit()
  }

  private class TagsLineListener : GitLineHandlerListener {
    private val tagsToHashes = mutableMapOf<GitTag, Hash>()
    private val hashesToTags = mutableMapOf<Hash, MutableList<GitTag>>()
    private var badLineReported = 0

    override fun onLineAvailable(line: @NlsSafe String, outputType: Key<*>) {
      try {
        if (outputType == ProcessOutputType.STDOUT) {
          val scanner = StringScanner(line)
          val tagName = scanner.tabToken() ?: return
          val dereferencedHash = scanner.tabToken() ?: return
          val tagHash = scanner.line() ?: return

          // Use dereferenced hash if available (for annotated tags), otherwise use tag hash (for lightweight tags)
          val commitHash = dereferencedHash.ifBlank { tagHash }
          addTag(tagName, commitHash)
        }
      }
      catch (e: VcsException) {
        badLineReported++
        if (badLineReported < 5) {
          LOG.warn("Unexpected output: $line", e)
        }
      }
    }

    private fun addTag(tagName: String, hash: String) {
      val gitTag = GitTag(tagName)
      val hashObj = HashImpl.build(hash)
      hashesToTags.getOrPut(hashObj) { mutableListOf() }.add(gitTag)
      tagsToHashes[gitTag] = hashObj
    }

    fun getTagsState(): GitRepositoryTagsState = LoadedState(tagsToHashes, hashesToTags)
  }

  @VisibleForTesting
  internal data class LoadedState(
    override val tagsToCommitHashes: Map<GitTag, Hash>,
    override val commitHashesToTags: Map<Hash, List<GitTag>>,
  ) : GitRepositoryTagsState

  companion object {
    private val LOG = logger<GitRepositoryTagsHolderImpl>()
  }
}

internal class GitRepositoryTagsHolderFetchHandler : GitFetchHandler {
  override fun doAfterSuccessfulFetch(
    project: Project,
    fetches: Map<GitRepository, List<GitRemote>>,
    indicator: ProgressIndicator,
  ) {
    fetches.keys.forEach { repository -> repository.tagsHolder.reload() }
  }
}
