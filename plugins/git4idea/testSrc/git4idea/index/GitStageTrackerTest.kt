// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.vcs.test.refresh
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.GitVcs
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<GitStageTrackerTest>()

@TestApplication
class GitStageTrackerTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  private lateinit var disposable: Disposable

  private lateinit var tracker: GitStageTracker

  @BeforeEach
  fun setUp(): Unit = with(context) {
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, VcsConfiguration.StandardConfirmation.ADD,
                                                VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY)
    runBlocking {
      repo.untrackedFilesHolder.awaitNotBusy()
    }
    tracker = object : GitStageTracker(project) {
      override fun isStagingAreaAvailable() = true
    }
    Disposer.register(disposable, tracker)
    project.messageBus.connect(tracker).subscribe(FileDocumentManagerListener.TOPIC, object : GitStageFileDocumentManagerListener() {
      override fun getTrackers(): List<GitStageTracker> = listOf(tracker)
    })
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : GitStageDocumentListener() {
      override fun getTrackers(): List<GitStageTracker> = listOf(tracker)
    }, tracker)
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      context.repo.untrackedFilesHolder.awaitNotBusy()
    }
  }

  @Test
  fun `test unstaged`(): Unit = with(context) {
    val fileName = "file.txt"
    touch(fileName, RandomStringUtils.insecure().nextAlphanumeric(200))
    git("add .")
    git("commit -m file")

    runWithTrackerUpdate("refresh") {
      refresh()
    }
    assertThat(trackerState().statuses).isEmpty()

    val file = projectRoot.findChild(fileName)!!
    val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(file)!! }

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.insecure().nextAlphanumeric(100)) } }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus(' ', 'M', VcsUtil.getFilePath(file)))

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus(' ', 'M', VcsUtil.getFilePath(file)))
  }

  @Test
  fun `test staged`(): Unit = with(context) {
    val fileName = "file.txt"
    touch(fileName, RandomStringUtils.insecure().nextAlphanumeric(200))
    git("add .")
    git("commit -m file")

    runWithTrackerUpdate("refresh") {
      refresh()
    }
    assertThat(trackerState().statuses).isEmpty()

    val file = projectRoot.findChild(fileName)!!
    val indexFile = project.service<GitIndexFileSystemRefresher>().createFile(projectRoot, VcsUtil.getFilePath(file))!!
    val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(indexFile)!! }

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.insecure().nextAlphanumeric(100)) } }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus('M', ' ', VcsUtil.getFilePath(file)))

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus('M', 'M', VcsUtil.getFilePath(file)))
  }

  @Test
  fun `test untracked`(): Unit = with(context) {
    val fileName = "file.txt"
    val file = invokeAndWaitIfNeeded { runWriteAction { projectRoot.createChildData(this, fileName) } }
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    assertThat(awaitTrackerStatus(VcsUtil.getFilePath(projectRoot, fileName)))
      .isEqualTo(GitFileStatus('?', '?', VcsUtil.getFilePath(file)))

    val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(file)!! }

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.insecure().nextAlphanumeric(100)) } }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus('?', '?', VcsUtil.getFilePath(file)))

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    assertThat(trackerState().statuses.getValue(VcsUtil.getFilePath(file)))
      .isEqualTo(GitFileStatus('?', '?', VcsUtil.getFilePath(file)))
  }

  private fun trackerState() = tracker.state.rootStates.getValue(context.projectRoot)

  private fun awaitTrackerStatus(filePath: FilePath): GitFileStatus = timeoutRunBlocking {
    var status = trackerState().statuses[filePath]
    while (status == null) {
      delay(10.milliseconds)
      status = trackerState().statuses[filePath]
    }
    status
  }

  private fun <T> runWithTrackerUpdate(name: String, function: () -> T): T {
    return tracker.futureUpdate(name).let { futureUpdate ->
      val result = function()
      context.changeListManager.waitEverythingDoneInTestMode()
      futureUpdate.waitOrCancel()
      return@let result
    }
  }

  private fun Future<Unit>.waitOrCancel() {
    try {
      get(2, TimeUnit.SECONDS)
    }
    finally {
      cancel(false)
    }
  }

  private fun GitStageTracker.futureUpdate(name: String): Future<Unit> {
    val removeListener = Disposer.newDisposable(this, "Listener disposable")
    val future = SettableFuture.create<Unit>()
    addListener(object : GitStageTrackerListener {
      override fun update() {
        LOG.debug("Refreshed tracker after \"$name\"")
        future.set(Unit)
      }
    }, removeListener)
    future.addListener(Runnable { Disposer.dispose(removeListener) }, MoreExecutors.directExecutor())
    return future
  }
}
