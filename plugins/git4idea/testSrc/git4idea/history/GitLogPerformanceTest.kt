// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.cancelOnDispose
import com.intellij.vcs.log.data.LoggingErrorHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogRefresherImpl
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitPlatformContextFixture
import git4idea.test.registerRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path

/**
 * **Local** suite for checking the performance of Git log operations in this repository.
 * It is not intended for running in CI environments and should be used for testing
 * hypotheses locally.
 */
@Disabled("Intended for local runs only")
@PerformanceUnitTest
@TestApplication
internal class GitLogPerformanceTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  companion object {
    private const val COMMITS_COUNT = 1000
    private const val ATTEMPTS = 10
    private val LOG = Logger.getInstance(GitLogPerformanceTest::class.java)
  }

  private lateinit var testRepo: GitRepository

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  private val repositoryPath: Path
    get() = PathManager.getHomeDir()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    TestLoggerFactory.enableTraceLogging(testRootDisposable, VcsLogRefresherImpl::class.java)
    testRepo = registerRepo(project, repositoryPath)
  }

  @Test
  @RegistryKey(key = "git.log.provider.experimental.refs.collection", value = "false")
  fun `test load and refresh log`(testInfo: TestInfo) {
    runLogRefreshBenchmark(testInfo.displayName)
  }

  @Test
  @RegistryKey(key = "git.log.provider.experimental.refs.collection", value = "true")
  fun `test load and refresh log experimental`(testInfo: TestInfo) {
    runLogRefreshBenchmark(testInfo.displayName)
  }

  private fun runLogRefreshBenchmark(testName: String) {
    testRepo.update()
    (testRepo.tagsHolder as GitRepositoryTagsHolderImpl).updateForTests()

    withTestScope { cs ->
      val refresher = createRefresher(cs)
      refresher.initialize()
      runBlocking {
        refresher.awaitNotBusy()
      }

      Benchmark.newBenchmark(testName) {
        refresher.refresh(listOf(testRepo.root), false)
        runBlocking {
          refresher.awaitNotBusy()
        }
      }.warmupIterations(0)
        .attempts(ATTEMPTS)
        .runAsStressTest()
        .start()
    }
  }

  private fun createRefresher(cs: CoroutineScope): VcsLogRefresherImpl = with(context) {
    val logData = VcsLogData(
      project,
      cs,
      mapOf(testRepo.root to logProvider),
      LoggingErrorHandler(LOG),
      false
    )
    return VcsLogRefresherImpl(
      cs,
      project,
      logData.storage,
      logData.logProviders,
      VcsLogProgress(project),
      null,
      { },
      COMMITS_COUNT
    )
  }

  private fun withTestScope(action: (scope: CoroutineScope) -> Unit) {
    @Suppress("RAW_SCOPE_CREATION")
    val cs = CoroutineScope(SupervisorJob()).also {
      it.coroutineContext.job.cancelOnDispose(testRootDisposable)
    }
    try {
      action(cs)
    }
    finally {
      runBlocking {
        cs.coroutineContext.job.cancelAndJoin()
      }
    }
  }
}
