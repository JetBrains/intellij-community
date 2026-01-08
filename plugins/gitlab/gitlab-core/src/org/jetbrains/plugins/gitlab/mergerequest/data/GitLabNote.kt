// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.async.Change
import com.intellij.collaboration.async.Deleted
import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.map
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import java.util.*

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

  val awardEmoji: StateFlow<List<GitLabAwardEmojiDTO>>

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
  private val _awardEmoji = MutableStateFlow<List<GitLabAwardEmojiDTO>>(emptyList())
  override val awardEmoji: StateFlow<List<GitLabAwardEmojiDTO>> = _awardEmoji.asStateFlow()
  override val position: StateFlow<GitLabNotePosition?> = data.mapState(cs) {
    it.position?.let(GitLabNotePosition::from)
  }
  override val positionMapping: StateFlow<ComputedResult<GitLabMergeRequestNotePositionMapping?>> =
    position.mapPosition(mr).stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())
  override fun canEdit(): Boolean = true
  override fun canSubmit(): Boolean = false

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
      withContext(Dispatchers.IO) {
        val id = id.guessGid() ?: return@withContext
        val awardEmojiTogglePayload = api.graphQL.awardEmojiToggle(id, reaction.name).body()
        updateEmojisLocally(reaction, awardEmojiTogglePayload)
      }
    }
  }

  fun update(item: GitLabNoteRestDTO) {
    data.value = item
  }

  fun setEmojis(updatedEmojis: List<GitLabAwardEmojiDTO>) {
    _awardEmoji.value = updatedEmojis
  }

  private fun updateEmojisLocally(reaction: GitLabReaction, awardEmojiTogglePayload: AwardEmojiTogglePayload) {
    val updatedEmojis = awardEmoji.value.toMutableList()
    val awardEmojiResponse = awardEmojiTogglePayload.awardEmoji // HINT: returned value is `null` if toggledOn is `false`
    if (awardEmojiTogglePayload.toggledOn && awardEmojiResponse != null) {
      updatedEmojis.add(awardEmojiResponse)
    }
    else {
      updatedEmojis.removeIf { it.name == reaction.name && it.user.id == currentUser.id }
    }

    setEmojis(updatedEmojis)
  }

  override fun toString(): String =
    "MutableGitLabNote(id='$id', author=$author, createdAt=$createdAt, canAdmin=$canAdmin, body=${body.value}, resolved=${resolved.value}, position=${position.value})"
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
  override val awardEmoji: StateFlow<List<GitLabAwardEmojiDTO>> = MutableStateFlow(emptyList())

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

