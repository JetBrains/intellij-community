// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.apitests

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDraftNoteRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.loadUpdatableJsonList
import org.jetbrains.plugins.gitlab.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabDiffPositionInput
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.startGitLabRestETagListLoaderIn
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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

      assertEquals(glTest1Mr2CommitShortShas, commits.map { it.shortId })
    }
  }

  @Test
  fun `REST loadMergeRequestChanges works as expected`() = runTest {
    checkVersion(inRange(v(9, 0), v(15, 7)))

    requiresAuthentication { api ->
      val changes = api.rest.loadMergeRequestChanges(api.getMergeRequestChangesURI(glTest1Coordinates, "2")).body()

      assertEquals(glTest1Mr2ChangedFiles, changes.changes.map { it.newPath })
    }
  }

  @Test
  fun `REST loadMergeRequestDiffs works as expected`() = runTest {
    checkVersion(after(v(15, 7)))

    requiresAuthentication { api ->
      val diffs = api.rest.loadMergeRequestDiffs(api.getMergeRequestDiffsURI(glTest1Coordinates, "2", 1)).body()

      assertEquals(glTest1Mr2ChangedFiles, diffs.map { it.newPath })
    }
  }

  @Test
  fun `REST loadCommitDiffs works as expected`() = runTest {
    checkVersion(after(v(7, 0)))

    requiresAuthentication { api ->
      val diffs = api.rest.loadCommitDiffs(getCommitDiffsURI(glTest1Coordinates, glTest1Mr2CommitShortShas[0])).body()

      assertEquals(glTest1Mr2ChangedFiles, diffs.map { it.newPath })
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
  fun `GQL loadMergeRequestDiscussions works as expected`() = runTest {
    checkVersion(after(v(12, 3)))

    requiresAuthentication { api ->
      val discussions = api.graphQL.loadMergeRequestDiscussions(glTest1Coordinates, "2")?.nodes

      assertNotNull(discussions)
      assertEquals(3, discussions?.size)
      assertEquals("Finished", discussions!![1].notes[0].body)
      assertEquals("I agree", discussions[1].notes[1].body)
      assertTrue(discussions[1].notes[1].resolved)
    }
  }

  @Test
  fun `REST and GQL read, create, update, delete note works`() = runTest {
    checkVersion(after(v(12, 3)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a new comment! ID=$randomId"
      val addNoteResult = api.graphQL.addNote(volatileProjectMr1Gid, initialBody).body()
      addNoteResult.assertNoErrors()

      val nextBody = "Changed comment! ID=$randomId"
      val updateNoteResult = api.graphQL.updateNote(addNoteResult!!.value!!.notes[0].id.gid, nextBody).body()
      updateNoteResult.assertNoErrors()

      // Check body changed
      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val updatedNote = api.graphQL.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid)
        ?.nodes?.find { it.id == addNoteResult.value!!.id }
      assertEquals(nextBody, updatedNote!!.notes[0].body)

      val deleteNoteResult = api.graphQL.deleteNote(addNoteResult.value!!.notes[0].id.gid).body()
      deleteNoteResult.assertNoErrors()

      // Check is deleted
      val deletedNote = api.graphQL.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid)
        ?.nodes?.find { it.id == addNoteResult.value!!.id }
      assertNull(deletedNote)
    }
  }

  @Test
  fun `GQL create and delete diff note works`() = runTest {
    checkVersion(after(v(12, 1)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a diff note! ID=$randomId"
      val addNoteResult = api.graphQL.addDiffNote(
        volatileProjectMr1Gid,
        GitLabDiffPositionInput("bd857928", "bd857928", 1, "063282e5", 1,
                                DiffPathsInput("README.md", null)),
        initialBody
      ).body()
      addNoteResult.assertNoErrors()

      val deleteNoteResult = api.graphQL.deleteNote(addNoteResult!!.value!!.notes[0].id.gid).body()
      deleteNoteResult.assertNoErrors()
    }
  }

  @Test
  fun `GQL create reply note works`() = runTest {
    checkVersion(after(v(12, 1)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a note! ID=$randomId"
      val addNoteResult = api.graphQL.addNote(
        volatileProjectMr1Gid,
        initialBody
      ).body()
      addNoteResult.assertNoErrors()

      val replyBody = "This is a reply! ID=$randomId"
      val addNoteResult2 = api.graphQL.createReplyNote(
        volatileProjectMr1Gid,
        addNoteResult!!.value!!.id.gid,
        replyBody
      ).body()
      addNoteResult2.assertNoErrors()

      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val result = api.graphQL.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid)
      val discussion = result?.nodes?.find { addNoteResult.value!!.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion)

      val deleteNoteResult1 = api.graphQL.deleteNote(discussion!!.notes[0].id.gid).body()
      deleteNoteResult1.assertNoErrors()
      val deleteNoteResult2 = api.graphQL.deleteNote(discussion.notes[1].id.gid).body()
      deleteNoteResult2.assertNoErrors()
    }
  }

  @Test
  fun `GQL resolve works`() = runTest {
    checkVersion(after(v(12, 1)))

    requiresAuthentication { api ->
      val randomId = Random.nextLong()
      val initialBody = "This is a diff note! ID=$randomId"
      val addNoteResult = api.graphQL.addDiffNote(
        volatileProjectMr1Gid,
        GitLabDiffPositionInput("bd857928", "bd857928", 1, "063282e5", 1,
                                DiffPathsInput("README.md", null)),
        initialBody
      ).body()
      addNoteResult.assertNoErrors()

      val replyBody = "This is a reply! ID=$randomId"
      val addNoteResult2 = api.graphQL.createReplyNote(
        volatileProjectMr1Gid,
        addNoteResult!!.value!!.id.gid,
        replyBody
      ).body()
      addNoteResult2.assertNoErrors()

      // NOTE: WILL NOT WORK ON A CONSTANTLY RUNNING SERVER BECAUSE OF PAGINATION
      val result1 = api.graphQL.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid)
      val discussion1 = result1?.nodes?.find { addNoteResult.value!!.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion1)
      assertFalse(discussion1!!.notes[0].resolved)

      val resolveNoteResult = api.graphQL.changeMergeRequestDiscussionResolve(discussion1.replyId.gid, true).body()
      resolveNoteResult.assertNoErrors()

      // Confirm is now resolved
      val result2 = api.graphQL.loadMergeRequestDiscussions(volatileProjectCoordinates, volatileProjectMr1Iid)
      val discussion2 = result2?.nodes?.find { addNoteResult.value!!.notes[0].body.contains(randomId.toString()) }
      assertNotNull(discussion2)
      assertTrue(discussion2!!.notes[0].resolved)

      val deleteNoteResult1 = api.graphQL.deleteNote(discussion2.notes[0].id.gid).body()
      deleteNoteResult1.assertNoErrors()
      val deleteNoteResult2 = api.graphQL.deleteNote(discussion2.notes[1].id.gid).body()
      deleteNoteResult2.assertNoErrors()
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

      assertEquals(listOf("this is a draft note!"), draftNotes.map { it.note })
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

      assertEquals(listOf("2"), mrs?.map { it.iid })
    }
  }

  @Test
  fun `GQL searching MRs works`() = runTest {
    checkVersion(after(v(12, 0)))

    requiresAuthentication { api ->
      val mr = api.graphQL.loadMergeRequest(glTest1Coordinates, "2").body()

      assertNotNull(mr)
      assertEquals("2", mr!!.iid)
    }
  }

  @Test
  fun `GQL findMergeRequestsByBranch works`() = runTest {
    checkVersion(after(v(13, 1)))

    requiresAuthentication { api ->
      val mrs = api.graphQL.findMergeRequestsByBranch(glTest1Coordinates, GitLabMergeRequestState.ALL, "changes-on-b").body()

      assertNotNull(mrs)
      assertEquals(listOf("3"), mrs!!.nodes.map { it.iid })
    }
  }

  @Test
  fun `GQL getMergeRequestStateEvents works`() = runTest {
    checkVersion(after(v(13, 2)))

    val cs = childScope()
    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(cs,
                                                   getMergeRequestStateEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceStateEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertEquals(listOf(1), result?.map { it.id })
    }
    cs.cancelAndJoinSilently()
  }

  @Test
  fun `GQL getMergeRequestLabelEvents works`() = runTest {
    checkVersion(after(v(11, 4)))

    val cs = childScope()
    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(cs,
                                                   getMergeRequestLabelEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceLabelEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertEquals(listOf(3, 4, 5), result?.map { it.id })
    }
    cs.cancelAndJoinSilently()
  }

  @Test
  fun `GQL getMergeRequestMilestoneEvents works`() = runTest {
    checkVersion(after(v(13, 1)))

    val cs = childScope()
    requiresAuthentication { api ->
      val reloadRequest = MutableSharedFlow<Unit>(1).withInitial(Unit)
      val loader = startGitLabRestETagListLoaderIn(cs,
                                                   getMergeRequestMilestoneEventsUri(glTest1Coordinates, "1"),
                                                   { it.id },
                                                   reloadRequest) { uri, eTag ->
        api.rest.loadUpdatableJsonList<GitLabResourceMilestoneEventDTO>(
          GitLabApiRequestName.REST_GET_MERGE_REQUEST_STATE_EVENTS, uri, eTag
        )
      }
      val result = loader.stateFlow.first { it.list != null }.list

      assertNotNull(result)
      assertEquals(listOf(3, 4), result?.map { it.id })
    }
    cs.cancelAndJoinSilently()
  }

  @Test
  fun `REST mergeRequestApprove and mergeRequestUnApprove does not error`() = runTest {
    checkMetadata {
      (it.version >= v(10, 6) && it.edition == GitLabEdition.Enterprise) ||
      (it.version < v(14, 3) && it.edition == GitLabEdition.Community)
    }

    requiresAuthentication { api ->
      api.rest.mergeRequestApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      api.rest.mergeRequestUnApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
    }
  }

  @Test
  fun `REST mergeRequestApprove and mergeRequestUnApprove works`() = runTest {
    // The approved field is only available for Enterprise...
    checkMetadata {
      (it.version >= v(14, 3) && it.edition == GitLabEdition.Enterprise)
    }

    requiresAuthentication { api ->
      api.rest.mergeRequestApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      var mr = api.graphQL.loadMergeRequest(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      assertNotNull(mr)

      api.rest.mergeRequestUnApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      mr = api.graphQL.loadMergeRequest(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      assertNotNull(mr)

      // Do it one more time to confirm the MR wasn't already approved before the first approve
      api.rest.mergeRequestApprove(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      mr = api.graphQL.loadMergeRequest(volatileProjectCoordinates, volatileProjectMr2Iid).body()
      assertNotNull(mr)
    }
  }

  @Test
  fun `GQL createAllProjectLabelsFlow works as expected`() = runTest {
    checkVersion(after(v(13, 1)))

    requiresAuthentication { api ->
      val flow = api.graphQL.createAllProjectLabelsFlow(glTests2Coordinates)
      val labels = flow.foldToList().toSet()

      assertEquals(setOf(testsGroupLabel1, testsGroupLabel2, glTests2Label1, glTests2Label2), labels)
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
      assertEquals(edition, guessedEdition)
    }
  }

  @Test
  fun `GQL getServerMetadata gets correct version and edition starting with GL 15_6`() = runTest {
    checkVersion(after(v(15, 6)))

    requiresAuthentication { api ->
      val metadata = api.graphQL.getServerMetadata().body()

      assertNotNull(metadata)
      requireNotNull(metadata)

      assertEquals(version.toString(), metadata.version)
      assertEquals(edition == GitLabEdition.Enterprise, metadata.enterprise)
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
}