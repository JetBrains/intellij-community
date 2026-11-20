// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
class GitBinaryHandlerCancellationTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `cancel destroys the git process`(): Unit = timeoutRunBlocking(30.seconds) {
    val handler = ObservedGitBinaryHandler(context.project, context.repo.root, GitCommand.CAT_FILE)
    // with the open stdin, `git cat-file --batch` runs until it is killed
    handler.addParameters("--batch")

    val job = launch(Dispatchers.IO) {
      coroutineToIndicator { handler.run() }
    }
    val process = handler.process.await()
    job.cancelAndJoin()

    val exited = withContext(Dispatchers.IO) { process.waitFor(10, TimeUnit.SECONDS) }
    assertThat(exited).describedAs("git process is alive after the cancel").isTrue()
  }

  private class ObservedGitBinaryHandler(project: Project, root: VirtualFile, command: GitCommand)
    : GitBinaryHandler(project, root, command) {
    val process = CompletableDeferred<Process>()

    override fun startProcess(): Process = super.startProcess().also { process.complete(it) }
  }
}
