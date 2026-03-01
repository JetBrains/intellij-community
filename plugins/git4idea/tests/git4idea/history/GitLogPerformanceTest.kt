// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vcs.impl.VcsInitialization
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.disposeOnCompletion
import com.intellij.vcs.log.data.LoggingErrorHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogRefresherImpl
import git4idea.commands.GitHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.GitPlatformTest
import git4idea.test.registerRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import java.nio.file.Path

/**
 * **Local** suite for checking the performance of Git log operations in this repository.
 * It is not intended for running in CI environments and should be used for testing
 * hypotheses locally.
 */
@Ignore("Intended for local runs only")
@PerformanceUnitTest
internal class GitLogPerformanceTest : GitPlatformTest() {
  private lateinit var testRepo: GitRepository

  private val repositoryPath: Path
    get() = PathManager.getHomeDir()

  override fun setUp() {
    TestLoggerFactory.enableTraceLogging(testRootDisposable, VcsLogRefresherImpl::class.java)
    super.setUp()
    testRepo = registerRepo(project, repositoryPath)
  }

  override fun getDebugLogCategories(): Collection<String> = listOf(
    "#" + UsefulTestCase::class.java.name,
    "#" + NewMappings::class.java.name,
    "#" + VcsInitialization::class.java.name,
    "#time." + GitHandler::class.java.name,
  )

  fun `test load and refresh log`() {
    setRegistryPropertyForTest("git.log.provider.experimental.refs.collection", false.toString())
    runLogRefreshBenchmark()
  }

  fun `test load and refresh log experimental`() {
    setRegistryPropertyForTest("git.log.provider.experimental.refs.collection", true.toString())
    runLogRefreshBenchmark()
  }

  private fun runLogRefreshBenchmark() {
    testRepo.update()
    (testRepo.tagsHolder as GitRepositoryTagsHolderImpl).updateForTests()

    withTestScope { cs ->
      val refresher = createRefresher(cs)
      refresher.initialize()
      runBlocking {
        refresher.awaitNotBusy()
      }

      Benchmark.newBenchmark(getTestName(false)) {
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

  private fun createRefresher(cs: CoroutineScope): VcsLogRefresherImpl {
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
    val cs = CoroutineScope(SupervisorJob()).also { testRootDisposable.disposeOnCompletion(it) }
    try {
      action(cs)
    }
    finally {
      runBlocking {
        cs.coroutineContext.job.cancelAndJoin()
      }
    }
  }

  companion object {
    private const val COMMITS_COUNT = 1000
    private const val ATTEMPTS = 10
  }
}
