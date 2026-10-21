// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.github.api.DEFAULT_MAX_CONCURRENT_REQUESTS
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiContentHelper
import org.jetbrains.plugins.github.api.GithubApiHelperRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests.Repos.Commits
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.commit.GHCommitFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

private const val COMMITS_COUNT = MAX_CONCURRENT_COMMITS * 5
private const val BLOCKING_REQUESTS_COUNT = DEFAULT_MAX_CONCURRENT_REQUESTS * 5
private const val FILES_PER_PAGE = 3
private const val DIFF_PAGES_PER_COMMIT = 2

/**
 * The time that the server holds each request.
 * It is long enough for more than [DEFAULT_MAX_CONCURRENT_REQUESTS] requests to overlap.
 */
private const val RESPONSE_DELAY_MS = 200L

private const val REPO_PREFIX = "/api/v3/repos/owner/repo"
private const val MERGE_BASE_SHA = "merge-base"
private val PR_ID = GHPRIdentifier("pr", 1)

/**
 * Loads pull request changes from a local HTTP server that limits concurrent requests.
 *
 * The server serves at most [DEFAULT_MAX_CONCURRENT_REQUESTS] requests at the same time.
 * It drops the connection of each request above this number.
 *
 * Each commit diff has two pages, so the load sends two requests for each commit.
 * The case runs against [GithubApiHelperRequestExecutor], the only executor with the limit.
 */
@TestApplication
internal class GHPRChangesServiceLoadTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var server: HttpServer
  private lateinit var serverExecutor: ExecutorService
  private lateinit var repository: GHRepositoryCoordinates

  private val inFlight = AtomicInteger()
  private val maxInFlight = AtomicInteger()
  private val rejected = AtomicInteger()
  private val activeCommits = AtomicInteger()
  private val maxActiveCommits = AtomicInteger()

  @BeforeEach
  fun startServer() {
    Registry.get(GithubApiRequestExecutor.Factory.JDK11_CLIENT_REGISTRY_KEY).setValue(true, testDisposable)
    // a big backlog makes sure that only the concurrency limit drops a connection
    server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), maxOf(COMMITS_COUNT, BLOCKING_REQUESTS_COUNT) * 4)
    serverExecutor = Executors.newCachedThreadPool()
    server.executor = serverExecutor
    server.createContext("/") { exchange ->
      exchange.use {
        val current = inFlight.incrementAndGet()
        maxInFlight.accumulateAndGet(current, ::maxOf)
        try {
          if (current > DEFAULT_MAX_CONCURRENT_REQUESTS) {
            rejected.incrementAndGet()
            // closing the exchange without the response headers drops the connection
            return@use
          }
          Thread.sleep(RESPONSE_DELAY_MS)
        }
        finally {
          // the request stops counting before the response, because the client may send the next request as soon as it reads the response
          inFlight.decrementAndGet()
        }
        answer(it)
      }
    }
    server.start()
    val serverPath = GithubServerPath(true, "127.0.0.1", server.address.port, null)
    repository = GHRepositoryCoordinates(serverPath, GHRepositoryPath("owner", "repo"))
  }

  @AfterEach
  fun stopServer() {
    server.stop(0)
    serverExecutor.shutdownNow()
    unmockkAll()
  }

  @Test
  fun `a pull request loads commits within the concurrent commit limit`(): Unit = timeoutRunBlocking(2.minutes) {
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(repository.serverPath, "test-token")
    val serviceCs = childScope("GHPRChangesServiceLoadTest")
    val service = GHPRChangesServiceImpl(serviceCs, mockk(), executor, createGitRemote(), repository)
    val commits = createCommits()

    val result = try {
      service.createChangesProvider(PR_ID, MERGE_BASE_SHA, MERGE_BASE_SHA, commits.last().oid, commits)
    }
    catch (e: IOException) {
      fail("The load failed. Peak of concurrent requests: ${maxInFlight.get()}, dropped requests: ${rejected.get()}", e)
    }
    finally {
      serviceCs.cancel()
    }

    assertWithinClientLimit()
    assertEquals((1..COMMITS_COUNT).map { "commit-$it" }, result.commits.map { it.sha })
    for ((index, commit) in result.commits.withIndex()) {
      val expectedPaths = (1..DIFF_PAGES_PER_COMMIT).flatMap { page -> (1..FILES_PER_PAGE).map { "dir${index + 1}/file${page}_$it.txt" } }
      assertEquals(expectedPaths, commit.patches.map { it.afterName })
    }
    assertEquals(COMMITS_COUNT * FILES_PER_PAGE * DIFF_PAGES_PER_COMMIT, result.changes.size)
    assertEquals(0, activeCommits.get())
    assertTrue(maxActiveCommits.get() <= MAX_CONCURRENT_COMMITS) {
      "Peak active commits: ${maxActiveCommits.get()}, limit: $MAX_CONCURRENT_COMMITS. " +
      "Peak concurrent requests: ${maxInFlight.get()}, dropped requests: ${rejected.get()}"
    }
  }

  @Test
  fun `blocking requests from many threads stay within the concurrency limit of the server`() {
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(repository.serverPath, "test-token")
    val threads = Executors.newFixedThreadPool(BLOCKING_REQUESTS_COUNT)
    try {
      val futures = (1..BLOCKING_REQUESTS_COUNT).map { i ->
        threads.submit<List<GHCommitFile>> {
          executor.execute(EmptyProgressIndicator(), Commits.getDiffFiles(repository, "commit-$i")).items
        }
      }
      val files = try {
        futures.flatMap { it.get(2, TimeUnit.MINUTES) }
      }
      catch (e: ExecutionException) {
        fail("The load failed. Peak of concurrent requests: ${maxInFlight.get()}, dropped requests: ${rejected.get()}", e)
      }

      assertWithinClientLimit()
      assertEquals(BLOCKING_REQUESTS_COUNT * FILES_PER_PAGE, files.size)
    }
    finally {
      threads.shutdownNow()
    }
  }

  private fun assertWithinClientLimit() {
    assertEquals(0, rejected.get()) {
      "Peak of concurrent requests ${maxInFlight.get()} is above the limit $DEFAULT_MAX_CONCURRENT_REQUESTS, dropped requests: ${rejected.get()}"
    }
  }

  private fun answer(exchange: HttpExchange) {
    val path = exchange.requestURI.path
    val page = exchange.requestURI.query?.removePrefix("page=")?.toInt() ?: 1
    when {
      path == "$REPO_PREFIX/pulls/${PR_ID.number}/files" -> {
        val files = (1..COMMITS_COUNT).flatMap { commit -> (1..DIFF_PAGES_PER_COMMIT).flatMap { filesJson(commit, it) } }
        respond(exchange, files.joinToString(",", "[", "]"))
      }
      path.startsWith("$REPO_PREFIX/commits/") -> {
        val commit = path.substringAfterLast("-").toInt()
        if (page == 1) {
          maxActiveCommits.accumulateAndGet(activeCommits.incrementAndGet(), ::maxOf)
          exchange.responseHeaders.add("Link", """<http://127.0.0.1:${server.address.port}$path?page=$DIFF_PAGES_PER_COMMIT>; rel="next"""")
        }
        else {
          activeCommits.decrementAndGet()
        }
        respond(exchange, filesJson(commit, page).joinToString(",", """{"files":[""", "]}"))
      }
      else -> {
        exchange.sendResponseHeaders(404, -1)
      }
    }
  }

  private fun respond(exchange: HttpExchange, body: String) {
    val bytes = body.toByteArray()
    exchange.responseHeaders.add("Content-Type", GithubApiContentHelper.JSON_MIME_TYPE)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.write(bytes)
  }
}

private fun filesJson(commit: Int, page: Int): List<String> = (1..FILES_PER_PAGE).map { file ->
  """{"filename":"dir$commit/file${page}_$file.txt","status":"added","patch":"@@ -0,0 +1 @@\n+line"}"""
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

private fun createCommits(): List<GHCommit> {
  var parentSha = MERGE_BASE_SHA
  return (1..COMMITS_COUNT).map { i ->
    val sha = "commit-$i"
    GHCommit(id = sha,
             oid = sha,
             abbreviatedOid = sha,
             url = "",
             messageHeadline = "Commit $i",
             messageBody = "",
             author = null,
             committer = null,
             committedDate = Date(),
             parents = GraphQLNodesDTO(listOf(GHCommitHash("", parentSha, parentSha)))).also { parentSha = sha }
  }
}
