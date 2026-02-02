// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.Change
import com.intellij.collaboration.async.Deleted
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.map
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabRestId
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.data.GitLabAwardEmoji
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addAwardEmoji
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteAwardEmoji
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMRNotesAwardEmojiUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.submitSingleDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateDraftNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateNote
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.util.Date

@CodeReviewDomainEntity
interface GitLabNote {
  val id: GitLabId
  val author: GitLabUserDTO
  val createdAt: Date?

  val body: StateFlow<String>
  val resolved: StateFlow<Boolean>
}

@CodeReviewDomainEntity
interface MutableGitLabNote : GitLabNote {
  val canAdmin: Boolean

  /**
   * Whether the note can be edited.
   */
  fun canEdit(): Boolean

  /**
   * Whether the note can be submitted individually.
   */
  fun canSubmit(): Boolean

  suspend fun setBody(newText: String)
  suspend fun delete()

  /**
   * Individually submit the note if it's a draft. This means the draft note is published as a note
   * shown to all users involed in the review, rather than just to the user that made the note.
   */
  suspend fun submit()
}

@CodeReviewDomainEntity
interface GitLabMergeRequestNote : GitLabNote {
  val canReact: Boolean
  val position: StateFlow<GitLabNotePosition?>
  val positionMapping: StateFlow<ComputedResult<GitLabMergeRequestNotePositionMapping?>>

  val awardEmojis: StateFlow<List<GitLabAwardEmoji>>

  suspend fun toggleReaction(reaction: GitLabReaction)
}

@CodeReviewDomainEntity
interface GitLabMergeRequestDraftNote : GitLabMergeRequestNote, MutableGitLabNote {
  val discussionId: GitLabId?
  override val createdAt: Date? get() = null
  override val canAdmin: Boolean get() = true
  override val canReact: Boolean get() = false
  override val resolved: StateFlow<Boolean> get() = MutableStateFlow(false)
}

class MutableGitLabMergeRequestNote(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest,
  private val discussionId: GitLabRestId,
  private val currentUser: GitLabUserDTO,
  private val eventSink: suspend (GitLabNoteEvent<GitLabNoteRestDTO>) -> Unit,
  noteData: GitLabNoteRestDTO,
) : GitLabMergeRequestNote, MutableGitLabNote {

  private val cs = parentCs.childScope(this::class)

  private val operationsGuard = Mutex()
  override val id: GitLabRestId = noteData.id
  override val author: GitLabUserDTO = GitLabUserDTO.fromRestDTO(noteData.author)
  override val createdAt: Date = noteData.createdAt
  override val canAdmin: Boolean = noteData.author.id == currentUser.id.substringAfterLast('/')
  override val canReact: Boolean = !noteData.system

  private val data = MutableStateFlow(noteData)
  override val body: StateFlow<String> = data.mapState(cs, GitLabNoteRestDTO::body)
  override val resolved: StateFlow<Boolean> = data.mapState(cs, GitLabNoteRestDTO::resolved)
  override val position: StateFlow<GitLabNotePosition?> = data.mapState(cs) {
    it.position?.let(GitLabNotePosition::from)
  }
  override val positionMapping: StateFlow<ComputedResult<GitLabMergeRequestNotePositionMapping?>> =
    position.mapPosition(mr).stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())
  private val _awardEmojis = MutableStateFlow<List<GitLabAwardEmoji>>(emptyList())
  override val awardEmojis: StateFlow<List<GitLabAwardEmoji>> = _awardEmojis.asStateFlow()
  private val emojiMapDeferred = service<GitLabEmojiService>().emojiMap

  override fun canEdit(): Boolean = true
  override fun canSubmit(): Boolean = false

  init {
    cs.launch {
      data.collect { loadEmojis() }
    }
  }

  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.rest.updateNote(project, mr.iid, discussionId.restId, id.restId, newText).body()
        }
      }
      data.update { it.copy(body = newText) }
    }
  }

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          api.rest.deleteNote(project, mr.iid, discussionId.restId, id.restId).body()
        }
      }
      eventSink(GitLabNoteEvent.Deleted(id))
    }
  }

  override suspend fun submit() {
    error("Cannot submit an already submitted note")
  }

  override suspend fun toggleReaction(reaction: GitLabReaction) {
    withContext(cs.coroutineContext) {
      val emojiMap = emojiMapDeferred.await()
      val noteEmoji = awardEmojis.value.firstOrNull {
        it.name == reaction.name && it.user.id == currentUser.id
      }
      withContext(Dispatchers.IO) {
        if (noteEmoji == null) {
          val awardedEmojiDTO = api.rest.addAwardEmoji(project, mr.iid, id.restId, reaction.name).body()
          val awardEmoji = GitLabAwardEmoji.fromDto(awardedEmojiDTO, emojiMap)
          updateEmojisLocally(awardEmoji)
        }
        else {
          val awardId = noteEmoji.id.restId ?: return@withContext
          api.rest.deleteAwardEmoji(project, mr.iid, id.restId, awardId).body()
          updateEmojisLocally(noteEmoji)
        }
      }
    }
  }

  fun update(item: GitLabNoteRestDTO) {
    data.value = item
  }

  private fun updateEmojisLocally(toggledEmoji: GitLabAwardEmoji) {
    _awardEmojis.update { currentEmojis ->
      val updatedEmojis = currentEmojis.toMutableList()
      if (!updatedEmojis.remove(toggledEmoji)) {
        updatedEmojis.add(toggledEmoji)
      }
      updatedEmojis
    }
  }

  private suspend fun loadEmojis() {
    runCatching {
      val emojiMap = emojiMapDeferred.await()
      val uri = getMRNotesAwardEmojiUri(project, mr.iid, id.restId)

      val emojis: List<GitLabAwardEmojiRestDTO> = withContext(Dispatchers.IO) {
        ApiPageUtil.createPagesFlowByLinkHeader(uri) { pageUri ->
          api.rest.loadUpdatableJsonList<GitLabAwardEmojiRestDTO>(GitLabApiRequestName.REST_GET_NOTE_AWARD_EMOJI, pageUri)
        }
          .map { it.body() }
          .foldToList()
      }
      _awardEmojis.value = emojis.map { emoji -> GitLabAwardEmoji.fromDto(emoji, emojiMap) }
    }.getOrHandleException {
      LOG.warn("Failed to load award emojis", it)
    }
  }

  override fun toString(): String =
    "MutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, canAdmin=$canAdmin, body=${body.value}, resolved=${resolved.value}, position=${position.value})"

  companion object {
    internal val LOG = logger<GitLabNote>()
  }
}

@CodeReviewDomainEntity
class GitLabMergeRequestDraftNoteImpl(
  parentCs: CoroutineScope,
  private val api: GitLabApi,
  private val glMetadata: GitLabServerMetadata?,
  private val project: GitLabProjectCoordinates,
  private val mr: GitLabMergeRequest,
  private val eventSink: suspend (Change<GitLabMergeRequestDraftNoteRestDTO>) -> Unit,
  private val noteData: GitLabMergeRequestDraftNoteRestDTO,
  override val author: GitLabUserDTO
) : GitLabMergeRequestDraftNote, MutableGitLabNote {

  private val cs = parentCs.childScope(this::class)

  private val operationsGuard = Mutex()

  override val id: GitLabRestId = noteData.id
  override val discussionId: GitLabId? = noteData.discussionId

  private val data = MutableStateFlow(noteData)
  override val body: StateFlow<String> = data.mapState(cs, GitLabMergeRequestDraftNoteRestDTO::note)
  override val awardEmojis: StateFlow<List<GitLabAwardEmoji>> = MutableStateFlow(emptyList())

  override val position: StateFlow<GitLabNotePosition?> = data.mapState(cs) { it.position.let(GitLabNotePosition::from) }
  override val positionMapping: StateFlow<ComputedResult<GitLabMergeRequestNotePositionMapping?>> =
    position.mapPosition(mr).stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  override fun canEdit(): Boolean =
    glMetadata != null && GitLabVersion(15, 10) <= glMetadata.version
  override fun canSubmit(): Boolean =
    glMetadata != null && GitLabVersion(15, 10) <= glMetadata.version

  @SinceGitLab("15.10")
  override suspend fun setBody(newText: String) {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          // Checked by canEdit
          api.rest.updateDraftNote(project, mr.iid, noteData.id.restId, noteData.position, newText)
        }
      }
      data.update { it.copy(note = newText) }
    }
  }

  override suspend fun delete() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          // Shouldn't require extra check, delete and get draft notes was introduced in
          // the same update
          api.rest.deleteDraftNote(project, mr.iid, noteData.id.restId.toLong()).body()
        }
      }
      eventSink(Deleted { it.id == id })
    }
  }

  override suspend fun submit() {
    withContext(cs.coroutineContext) {
      operationsGuard.withLock {
        withContext(Dispatchers.IO) {
          // Shouldn't require extra check, delete and get draft notes was introduced in
          // the same update
          api.rest.submitSingleDraftNote(project, mr.iid, noteData.id.restId.toLong()).body()
        }
      }
      mr.refreshData()
    }
  }

  override suspend fun toggleReaction(reaction: GitLabReaction) {
    error("Cannot toggle reaction on draft note")
  }

  fun update(item: GitLabMergeRequestDraftNoteRestDTO) {
    data.value = item
  }

  override fun toString(): String =
    "GitLabMergeRequestDraftNoteImpl(id='$id', author=$author, createdAt=$createdAt, body=${body.value})"
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<GitLabNotePosition?>.mapPosition(mr: GitLabMergeRequest): Flow<ComputedResult<GitLabMergeRequestNotePositionMapping?>> =
  this.combineTransform(mr.changesComputationState()) { position, changesResult ->
    if (position == null) {
      emit(ComputedResult.success(null))
      return@combineTransform
    }
    try {
      changesResult.map { allChanges ->
        GitLabMergeRequestNotePositionMapping.map(allChanges, position)
      }.let {
        emit(it)
      }
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      emit(ComputedResult.failure(e))
    }
  }

class GitLabSystemNote(noteData: GitLabNoteRestDTO) : GitLabNote {

  override val id: GitLabRestId = noteData.id
  override val author: GitLabUserDTO = GitLabUserDTO.fromRestDTO(noteData.author)
  override val createdAt: Date = noteData.createdAt

  private val _body = MutableStateFlow(noteData.body)
  override val body: StateFlow<String> = _body.asStateFlow()
  override val resolved: StateFlow<Boolean> = MutableStateFlow(false)

  override fun toString(): String =
    "ImmutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, body=${_body.value})"
}

