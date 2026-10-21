// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffRefs
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

private const val FILES_PER_PAGE = 3
private const val DIFF_PAGES_PER_COMMIT = 2

/**
 * The time that the server holds each request.
 * It is long enough for the requests of different commits to overlap.
 */
private const val RESPONSE_DELAY_MS = 200L

/**
 * The maximum time that the first commit requests wait until the commit limit is in flight.
 */
private const val LIMIT_FILL_TIMEOUT_SECONDS = 10L

private const val PROJECT_ID = "123"
private const val PROJECT_PREFIX = "/api/v4/projects/$PROJECT_ID"
private const val MR_IID = "1"
private const val MERGE_BASE_SHA = "merge-base"

/**
 * REST is used to load the commits of a merge request below this version, so the server does not need to answer GraphQL.
 */
private val SERVER_METADATA = GitLabServerMetadata(GitLabVersion(14, 0), "revision", GitLabEdition.Community)

/**
 * Loads merge request changes from a local HTTP server and checks the active commit limit.
 *
 * Each commit sends a request for the commit itself and two requests for the two pages of its diff.
 */
@TestApplication
internal class GitLabMergeRequestChangesLoadTest {
  private lateinit var server: HttpServer
  private lateinit var serverExecutor: ExecutorService
  private lateinit var serverPath: GitLabServerPath

  private val activeCommits = AtomicInteger()
  private val maxActiveCommits = AtomicInteger()
  private var commitsCount = 0

  /**
   * Holds the first commit requests until the commit limit is in flight.
   * Then the peak does not depend on how fast the client sends the requests.
   */
  private lateinit var limitFilled: CountDownLatch
  private val limitFillTimedOut = AtomicBoolean()

  @BeforeEach
  fun startServer() {
    // a big backlog makes sure that the server does not drop a connection
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 200)
    serverExecutor = Executors.newCachedThreadPool()
    server.executor = serverExecutor
    server.createContext("/") { exchange ->
      exchange.use {
        // The client sends the other requests one by one, so only commit requests can fill the limit.
        if (it.requestURI.path.startsWith("$PROJECT_PREFIX/repository/commits/")) {
          limitFilled.countDown()
          if (!limitFilled.await(LIMIT_FILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            limitFillTimedOut.set(true)
          }
        }
        Thread.sleep(RESPONSE_DELAY_MS)
        answer(it)
      }
    }
    server.start()
    serverPath = GitLabServerPath("http://127.0.0.1:${server.address.port}")
  }

  @AfterEach
  fun stopServer() {
    server.stop(0)
    serverExecutor.shutdownNow()
    unmockkAll()
  }

  @Test
  fun `loads a single commit`(): Unit = checkCommitsLoadWithLimit(1)

  @Test
  fun `loads up to the commit limit`(): Unit = checkCommitsLoadWithLimit(MAX_CONCURRENT_COMMITS)

  @Test
  fun `loads one more than the commit limit`(): Unit = checkCommitsLoadWithLimit(MAX_CONCURRENT_COMMITS + 1)

  @Test
  fun `loads many commits`(): Unit = checkCommitsLoadWithLimit(MAX_CONCURRENT_COMMITS * 5)

  private fun checkCommitsLoadWithLimit(commitsCount: Int): Unit = timeoutRunBlocking(2.minutes) {
    this@GitLabMergeRequestChangesLoadTest.commitsCount = commitsCount
    val expectedActiveCommits = minOf(commitsCount, MAX_CONCURRENT_COMMITS)
    limitFilled = CountDownLatch(expectedActiveCommits)
    val serversManager = mockk<GitLabServersManager> {
      coEvery { getMetadata(any()) } returns SERVER_METADATA
    }
    val api = GitLabApiImpl(serversManager, serverPath) { "test-token" }
    val details = mockk<GitLabMergeRequestFullDetails> {
      every { iid } returns MR_IID
      every { diffRefs } returns GitLabDiffRefs(MERGE_BASE_SHA, "commit-$commitsCount", MERGE_BASE_SHA)
    }
    val changesCs = childScope("GitLabMergeRequestChangesLoadTest")
    val changes = GitLabMergeRequestChangesImpl(changesCs,
                                                mockk(),
                                                PROJECT_ID,
                                                GitLabProjectPath("owner", "repo"),
                                                createGitRemote(),
                                                api,
                                                SERVER_METADATA,
                                                details)

    val result = try {
      changes.getParsedChanges()
    }
    catch (e: IOException) {
      fail("The load failed. Peak of active commits: ${maxActiveCommits.get()}", e)
    }
    finally {
      changesCs.cancel()
    }

    // The client loads commits concurrently instead of one by one.
    assertFalse(limitFillTimedOut.get()) {
      "The client did not load $expectedActiveCommits commits concurrently in $LIMIT_FILL_TIMEOUT_SECONDS seconds, peak: ${maxActiveCommits.get()}"
    }
    assertEquals(expectedActiveCommits, maxActiveCommits.get())
    assertEquals(0, activeCommits.get())
    assertEquals((1..commitsCount).map { "commit-$it" }, result.commits.map { it.sha })
    for ((index, commit) in result.commits.withIndex()) {
      assertEquals(listOf(if (index == 0) MERGE_BASE_SHA else "commit-$index"), commit.parents)
      val expectedPaths = (1..DIFF_PAGES_PER_COMMIT).flatMap { page -> (1..FILES_PER_PAGE).map { "dir${index + 1}/file${page}_$it.txt" } }
      assertEquals(expectedPaths, commit.patches.map { it.afterName })
    }
    assertEquals(commitsCount * FILES_PER_PAGE * DIFF_PAGES_PER_COMMIT, result.changes.size)
  }

  private fun answer(exchange: HttpExchange) {
    val path = exchange.requestURI.path
    val page = exchange.requestURI.query?.removePrefix("page=")?.toInt() ?: 1
    when {
      path == "$PROJECT_PREFIX/merge_requests/$MR_IID/commits" -> {
        // GitLab returns the newest commit first
        respond(exchange, (commitsCount downTo 1).joinToString(",", "[", "]") { commitJson(it) })
      }
      path == "$PROJECT_PREFIX/merge_requests/$MR_IID/changes" -> {
        val files = (1..commitsCount).flatMap { commit -> (1..DIFF_PAGES_PER_COMMIT).flatMap { filesJson(commit, it) } }
        respond(exchange, files.joinToString(",", """{"changes":[""", "]}"))
      }
      path.startsWith("$PROJECT_PREFIX/repository/commits/") && path.endsWith("/diff") -> {
        val commit = path.removeSuffix("/diff").substringAfterLast("-").toInt()
        if (page == 1) {
          exchange.responseHeaders.add("Link",
                                       """<http://127.0.0.1:${server.address.port}$path?page=$DIFF_PAGES_PER_COMMIT>; rel="next"""")
        }
        else {
          activeCommits.decrementAndGet()
        }
        respond(exchange, filesJson(commit, page).joinToString(",", "[", "]"))
      }
      path.startsWith("$PROJECT_PREFIX/repository/commits/") -> {
        val commit = path.substringAfterLast("-").toInt()
        maxActiveCommits.accumulateAndGet(activeCommits.incrementAndGet(), ::maxOf)
        respond(exchange, commitJson(commit))
      }
      else -> {
        exchange.sendResponseHeaders(404, -1)
      }
    }
  }

  private fun respond(exchange: HttpExchange, body: String) {
    val bytes = body.toByteArray()
    exchange.responseHeaders.add(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.MIME_TYPE_JSON)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.write(bytes)
  }
}

private fun commitJson(commit: Int): String {
  val parentSha = if (commit == 1) MERGE_BASE_SHA else "commit-${commit - 1}"
  val date = "2026-01-01T00:00:00.000+00:00"
  return "{\"id\":\"commit-$commit\",\"short_id\":\"commit-$commit\",\"title\":\"Commit $commit\",\"message\":\"Commit $commit\",\"author_name\":\"author\",\"author_email\":\"author@example.com\",\"committer_name\":\"author\",\"committer_email\":\"author@example.com\",\"created_at\":\"$date\",\"authored_date\":\"$date\",\"committed_date\":\"$date\",\"web_url\":\"\",\"parent_ids\":[\"$parentSha\"]}"
}

private fun filesJson(commit: Int, page: Int): List<String> = (1..FILES_PER_PAGE).map { file ->
  val path = "dir$commit/file${page}_$file.txt"
  """{"old_path":"$path","new_path":"$path","new_file":true,"deleted_file":false,"renamed_file":false,"diff":"@@ -0,0 +1 @@\n+line\n"}"""
}

private fun createGitRemote(): GitRemoteUrlCoordinates {
  val vcsRoot = mockk<VirtualFile> {
    every { path } returns "/repo"
  }
  val gitRepository = mockk<GitRepository> {
    every { root } returns vcsRoot
  }
  return GitRemoteUrlCoordinates("http://127.0.0.1/owner/repo.git", mockk(), gitRepository)
}
