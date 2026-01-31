// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.apitests

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.api.request.checkIsGitLabServer
import org.jetbrains.plugins.gitlab.api.request.createAllProjectLabelsFlow
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.getProjectUsers
import org.jetbrains.plugins.gitlab.api.request.getProjectUsersURI
import org.jetbrains.plugins.gitlab.api.request.getServerMetadata
import org.jetbrains.plugins.gitlab.api.request.getServerVersion
import org.jetbrains.plugins.gitlab.api.request.guessServerEdition
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInputDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addDiffNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.addNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.changeMergeRequestDiscussionResolve
import org.jetbrains.plugins.gitlab.mergerequest.api.request.createReplyNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.deleteNote
import org.jetbrains.plugins.gitlab.mergerequest.api.request.findMergeRequestsByBranch
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getCommitDiffsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestChangesURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestCommitsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDiffsURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDiscussionsUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestDraftNotesUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestLabelEventsUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestListURI
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestMilestoneEventsUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestStateEventsUri
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommit
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadCommitDiffs
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestCommits
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiffs
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestDiscussions
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.mergeRequestUnApprove
import org.jetbrains.plugins.gitlab.mergerequest.api.request.updateNote
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.upload.markdownUploadFile
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import kotlin.random.Random

/**
 * Has to be a single test class containing all GitLab API tests, because otherwise
 * the setup will be done for every test class. Setting up the docker server takes
 * about 1.5 minutes, so it's not exactly a small cost.
 */
class GitLabApiTest : GitLabApiTestCase() {
  @Test
  fun `REST loadMergeRequestCommits works as expected`() = runTest {
    checkVersion(after(v(9, 0)))

    requiresAuthentication { api ->
      val commits = api.rest.loadMergeRequestCommits(api.getMergeRequestCommitsURI(glTest1Coordinates, "2")).body()

      assertIterableEquals(glTest1Mr2CommitShortShas, commits.map { it.shortId })
    }
  }

  @Test
  fun `REST loadMergeRequestChanges works as expected`() = runTest {
    checkVersion(inRange(v(9, 0), v(15, 7)))

    requiresAuthentication { api ->
      val changes = api.rest.loadMergeRequestChanges(api.getMergeRequestChangesURI(glTest1Coordinates, "2")).body()

      assertIterableEquals(glTest1Mr2ChangedFiles, changes.changes.map { it.newPath })
    }
  }

  @Test
  fun `REST loadMergeRequestDiffs works as expected`() = runTest {
    checkVersion(after(v(15, 7)))

    requiresAuthentication { api ->
      val diffs = api.rest.loadMergeRequestDiffs(api.getMergeRequestDiffsURI(glTest1Coordinates, "2", 1)).body()

      assertIterableEquals(glTest1Mr2ChangedFiles, diffs.map { it.newPath })
    }
  }

  @Test
  fun `REST loadCommitDiffs works as expected`() = runTest {
    checkVersion(after(v(7, 0)))

    requiresAuthentication { api ->
      val diffs = api.rest.loadCommitDiffs(getCommitDiffsURI(glTest1Coordinates, glTest1Mr2CommitShortShas[0])).body()

      assertIterableEquals(glTest1Mr2ChangedFiles, diffs.map { it.newPath })
    }
  }

  @Test
  fun `REST loadCommit works as expected`() = runTest {
    checkVersion(after(v(7, 0)))

    requiresAuthentication { api ->
      val commit = api.rest.loadCommit(glTest1Coordinates, glTest1Mr2CommitShortShas[0]).body()

      assertEquals(glTest1Mr2CommitShas[0], commit.id)
    }
  }

  @Test
  fun `REST loadMergeRequestDiscussions works as expected`() = runTest {
    checkVersion(after(v(10, 6)))

    requiresAuthentication { api ->
      val uri = getMergeRequestDiscussionsUri(glTest1Coordinates, "2")
      val discussions = ApiPageUtil.createPagesFlowByLinkHeader(uri) {
        api.rest.loadUpdatableJsonList<GitLabDiscussionRestDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_DISCUSSIONS, it
        )
      }
        .map { it.body() }
        .fold(listOf<GitLabDiscussionRestDTO>()) { l, r -> l + (r ?: listOf()) }

      assertNotNull(discussions)
      assertTrue(discussions.size >= 2)
      assertEquals("Finished", discussions[1].notes[0].body)
      assertEquals("I agree", discussions[1].notes[1].body)
      assertTrue(discussions[1].notes[1].resolved)
    }
  }

  @Test
  fun `REST read, create, update, delete note works`() = runTest {
    checkVersion(after(v(10, 6)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a new comment! ID=$randomId"
      val addNoteResult = api.rest.addNote(volatileProjectCoordinates, volatileProjectMr1Iid, initialBody)
      assertNotNull(addNoteResult)
      val addNoteResultValue = addNoteResult.body()
      assertNotNull(addNoteResultValue)

      val nextBody = "Changed comment! ID=$randomId"

      val updateNoteResult = api.rest.updateNote(volatileProjectCoordinates,
                                                 volatileProjectMr1Iid,
                                                 addNoteResultValue.id.toString(),
                                                 addNoteResultValue.notes.first().id.toString(),
                                                 nextBody)
      assertNotNull(updateNoteResult)

      // Check body changed
      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val discussions = api.rest.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid).body()
      val updatedNote = discussions.find { it.id.toString() == addNoteResultValue.id.toString() }
      assertNotNull(updatedNote)

      assertEquals(nextBody, updatedNote.notes[0].body)

      val deleteNoteResult = api.rest.deleteNote(volatileProjectCoordinates,
                                                 volatileProjectMr1Iid,
                                                 updatedNote.id.toString(),
                                                 updatedNote.notes.first().id.toString()).body()
      assertNotNull(deleteNoteResult)
      // Check is deleted
      val deletedNote = api.rest.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid).body()
        .find { it.id.toString() == addNoteResultValue.id.toString() }

      assertNull(deletedNote)
    }
  }

  @Test
  fun `REST create and delete diff note works`() = runTest {
    checkVersion(after(v(13, 2)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a diff note! ID=$randomId"
      val addNoteResult = api.rest.addDiffNote(
        volatileProjectCoordinates,
        volatileProjectMr1Iid,
        GitLabDiffPositionInput("bd857928", "bd857928", 1, "063282e5", 1,
                                DiffPathsInputDTO("README.md", null)),
        initialBody
      ).body()
      assertNotNull(addNoteResult)
      val deleteNoteResult = api.rest.deleteNote(volatileProjectCoordinates,
                                                 volatileProjectMr1Iid,
                                                 addNoteResult.id.toString(),
                                                 addNoteResult.notes[0].id.toString()).body()
      assertNotNull(deleteNoteResult)
    }
  }

  @Test
  fun `REST create reply note works`() = runTest {
    checkVersion(after(v(10, 6)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a note! ID=$randomId"
      val addNoteResult = api.rest.addNote(
        volatileProjectCoordinates,
        volatileProjectMr1Iid,
        initialBody
      ).body()
      assertNotNull(addNoteResult)

      val replyBody = "This is a reply! ID=$randomId"
      val addNoteResult2 = api.rest.createReplyNote(
        volatileProjectCoordinates,
        volatileProjectMr1Iid,
        addNoteResult.id.toString(),
        replyBody
      ).body()
      assertNotNull(addNoteResult2)

      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val result = api.rest.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid).body()
      assertNotNull(result)

      val discussion = result.find { addNoteResult.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion)

      val deleteNoteResult1 =
        api.rest.deleteNote(volatileProjectCoordinates, volatileProjectMr1Iid, discussion.id.toString(), discussion.notes[0].id.toString())
          .body()
      assertNotNull(deleteNoteResult1)
      val deleteNoteResult2 =
        api.rest.deleteNote(volatileProjectCoordinates, volatileProjectMr1Iid, discussion.id.toString(), discussion.notes[1].id.toString())
          .body()
      assertNotNull(deleteNoteResult2)
    }
  }

  @Test
  fun `REST resolve works`() = runTest {
    checkVersion(after(v(13, 2)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a diff note! ID=$randomId"
      val addNoteResult = api.rest.addDiffNote(
        volatileProjectCoordinates,
        volatileProjectMr1Iid,
        GitLabDiffPositionInput("bd857928", "bd857928", 1, "063282e5", 1,
                                DiffPathsInputDTO("README.md", null)),
        initialBody
      ).body()
      assertNotNull(addNoteResult)

      val replyBody = "This is a reply! ID=$randomId"
      val addNoteResult2 = api.rest.createReplyNote(
        volatileProjectCoordinates,
        volatileProjectMr1Iid,
        addNoteResult.id.toString(),
        replyBody
      ).body()
      assertNotNull(addNoteResult2)

      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val result1 = api.rest.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid).body()
      assertNotNull(result1)

      val discussion1 = result1.find { addNoteResult.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion1)
      assertFalse(discussion1.notes[0].resolved)

      val resolveNoteResult =
        api.rest.changeMergeRequestDiscussionResolve(volatileProjectCoordinates, volatileProjectMr1Iid, discussion1.id.toString(), true)
          .body()
      assertNotNull(resolveNoteResult)

      // Confirm is now resolved
      val result2 = api.rest.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid).body()
      assertNotNull(result2)

      val discussion2 = result2.find { addNoteResult.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion2)
      assertTrue(discussion2.notes[0].resolved)

      val deleteNoteResult1 = api.rest.deleteNote(volatileProjectCoordinates,
                                                  volatileProjectMr1Iid,
                                                  discussion2.id.toString(),
                                                  discussion2.notes[0].id.toString()).body()
      assertNotNull(deleteNoteResult1)
      val deleteNoteResult2 = api.rest.deleteNote(volatileProjectCoordinates,
                                                  volatileProjectMr1Iid,
                                                  discussion2.id.toString(),
                                                  discussion2.notes[1].id.toString()).body()
      assertNotNull(deleteNoteResult2)
    }
  }

  @Test
  fun `REST getting draft notes works`() = runTest {
    checkVersion(after(v(15, 9)))

    requiresAuthentication { api ->
      val uri = getMergeRequestDraftNotesUri(glTest1Coordinates, glTest1Mr2Iid)
      val draftNotes = ApiPageUtil.createPagesFlowByLinkHeader(uri) {
        api.rest.loadUpdatableJsonList<GitLabMergeRequestDraftNoteRestDTO>(
          GitLabApiRequestName.REST_GET_DRAFT_NOTES, it
        )
      }
        .map { it.body() }
        .fold(listOf<GitLabMergeRequestDraftNoteRestDTO>()) { l, r -> l + (r ?: listOf()) }

      assertIterableEquals(listOf("this is a draft note!"), draftNotes.map { it.note })
    }
  }

  @Test
  fun `REST searching MRs works`() = runTest {
    checkVersion(after(v(7, 0)))

    requiresAuthentication { api ->
      val mrs = api.rest.loadUpdatableJsonList<GitLabMergeRequestShortRestDTO>(
        GitLabApiRequestName.REST_GET_MERGE_REQUESTS,
        getMergeRequestListURI(glTest1Coordinates, "search=important")
      ).body()

      assertNotNull(mrs)
      assertIterableEquals(listOf("2"), mrs.map { it.iid })
    }
  }

  @Test
  fun `GQL searching MRs works`() = runTest {
    checkVersion(after(v(12, 0)))

    requiresAuthentication { api ->
      val mr = api.graphQL.loadMergeRequest(glTest1Coordinates, "2").body()

      assertNotNull(mr)
      assertEquals("2", mr.iid)
    }
  }

  @Test
  fun `GQL findMergeRequestsByBranch works`() = runTest {
    checkVersion(after(v(13, 1)))

    requiresAuthentication { api ->
      val mrs = api.graphQL.findMergeRequestsByBranch(glTest1Coordinates, GitLabMergeRequestState.ALL, "changes-on-b").body()

      assertNotNull(mrs)
      assertEquals(listOf("3"), mrs.nodes.map { it.iid })
    }
  }

  @Test
  fun `GQL getMergeRequestStateEvents works`() = runTest {
    checkVersion(after(v(13, 2)))

    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(backgroundScope,
                                                   getMergeRequestStateEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceStateEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertIterableEquals(listOf(1L), result.map { it.id })
    }
  }

  @Test
  fun `GQL getMergeRequestLabelEvents works`() = runTest {
    checkVersion(after(v(11, 4)))

    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(backgroundScope,
                                                   getMergeRequestLabelEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceLabelEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertIterableEquals(listOf(3L, 4L, 5L), result.map { it.id })
    }
  }

  @Test
  fun `GQL getMergeRequestMilestoneEvents works`() = runTest {
    checkVersion(after(v(13, 1)))

    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(backgroundScope,
                                                   getMergeRequestMilestoneEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceMilestoneEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertIterableEquals(listOf(3L, 4L), result.map { it.id })
    }
  }

  @Test
  fun `REST mergeRequestApprove and mergeRequestUnApprove does not error`() = runTest {
    checkMetadata {
      (it.version >= v(10, 6) && it.edition == GitLabEdition.Enterprise) ||
      (it.version >= v(13, 3) && it.edition == GitLabEdition.Community)
    }

    requiresAuthentication { api ->
      api.rest.mergeRequestApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      api.rest.mergeRequestUnApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
    }
  }

  @Test
  fun `GQL createAllProjectLabelsFlow works as expected`() = runTest {
    checkVersion(after(v(13, 1)))

    requiresAuthentication { api ->
      val flow = api.graphQL.createAllProjectLabelsFlow(glTests2Coordinates)
      val labels = flow.foldToList().sortedBy { it.title }

      assertIterableEquals(listOf(testsGroupLabel1, testsGroupLabel2, glTests2Label1, glTests2Label2), labels)
    }
  }

  @Test
  fun `REST getProjectUsers works as expected`() = runTest {
    checkVersion(after(v(9, 0)))

    requiresAuthentication { api ->
      val users = api.rest.getProjectUsers(getProjectUsersURI(glTests2Coordinates)).body()

      assertTrue(users.map { it.username }.contains(rootUsername))
    }
  }

  @Test
  fun `REST getCurrentUser works as expected`() = runTest {
    checkVersion(after(v(7, 0)))

    requiresAuthentication { api ->
      val user = api.rest.getCurrentUser().body()

      assertEquals(rootUsername, user.username)
    }
  }

  @Test
  fun `GQL getCurrentUser works as expected`() = runTest {
    checkVersion(after(v(12, 5)))

    requiresAuthentication { api ->
      val user = api.graphQL.getCurrentUser()

      assertNotNull(user)
      assertEquals(rootUsername, user.username)
    }
  }

  @Test
  fun `REST checkIsGitLabServer is positive for server`() = runTest {
    checkVersion(after(v(9)))

    requiresNoAuthentication { api ->
      assertTrue(api.rest.checkIsGitLabServer())
    }
  }

  @Test
  @Disabled("Find out what server to ping here, if any")
  fun `REST checkIsGitLabServer is negative for unrelated API`() = runTest {
    val api = service<GitLabApiManager>().getClient(GitLabServerPath("https://google.com"), "")
    assertFalse(api.rest.checkIsGitLabServer())
  }

  @Test
  fun `REST guessServerEdition correctly guesses server edition`() = runTest {
    checkVersion(after(v(9)))

    requiresNoAuthentication { api ->
      val guessedEdition = api.rest.guessServerEdition()
      assertNotNull(guessedEdition)
      assertEquals(edition, guessedEdition)
    }
  }

  @Test
  fun `GQL getServerMetadata gets correct version and edition starting with GL 15_6`() = runTest {
    checkVersion(after(v(15, 6)))

    requiresAuthentication { api ->
      val metadata = api.graphQL.getServerMetadata().body()
      assertNotNull(metadata)
      assertEquals(version.toString(), metadata.version)
      val isEnterprise = metadata.enterprise
      assertNotNull(isEnterprise)
      assertEquals(edition == GitLabEdition.Enterprise, isEnterprise)
    }
  }

  @Test
  fun `REST getServerVersion gets correct version until 15_7`() = runTest {
    checkVersion(inRange(v(8, 13), v(15, 7)))

    requiresAuthentication { api ->
      val actualVersion = api.rest.getServerVersion().body()
      assertEquals(version.toString(), actualVersion.version)
    }
  }

  @Test
  fun `REST markdownUploadFile can upload a file`() = runTest {
    checkVersion(after(v(15, 10)))

    requiresAuthentication { api ->
      javaClass.getResourceAsStream("/upload/test-png.png").use {
        assertNotNull(it, "test-png.png resource should exist")
        val name = "test-image"
        val filename = "$name.png"
        val uploadResult = api.rest.markdownUploadFile(glTest1Coordinates, filename, "image/png", it).body()
        assertNotNull(uploadResult)
        val markdown = uploadResult.markdown
        assertTrue(markdown.startsWith("![$name](/uploads/"),
                   """Markdown should start with "![$name](/uploads/", current markdown: $markdown""")
        assertTrue(markdown.endsWith("/$filename)"),
                   """Markdown should end with "/$filename)", current markdown: $markdown""")
      }
    }
  }
}