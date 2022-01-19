// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.AppTopics
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.testFramework.UsefulTestCase
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase
import org.apache.commons.lang.RandomStringUtils
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class GitStageTrackerTest : GitSingleRepoTest() {
  private var _tracker: GitStageTracker? = null

  private val tracker get() = _tracker!!

  override fun setUp() {
    super.setUp()
    VcsConfiguration.StandardConfirmation.ADD.doNothing()
    repo.untrackedFilesHolder.createWaiter().waitFor()
    _tracker = object : GitStageTracker(project) {
      override fun isStagingAreaAvailable() = true
    }
    project.messageBus.connect(tracker).subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : GitStageFileDocumentManagerListener() {
      override fun getTrackers(): List<GitStageTracker> = listOf(tracker)
    })
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : GitStageDocumentListener() {
      override fun getTrackers(): List<GitStageTracker> = listOf(tracker)
    }, tracker)
  }

  override fun tearDown() {
    val t = _tracker
    _tracker = null
    t?.let { Disposer.dispose(it) }
    repo.untrackedFilesHolder.createWaiter().waitFor()
    super.tearDown()
  }

  fun `test unstaged`() {
    val fileName = "file.txt"
    Executor.touch(fileName, RandomStringUtils.randomAlphanumeric(200))
    git("add .")
    git("commit -m file")

    runWithTrackerUpdate("refresh") {
      refresh()
    }
    assertTrue(trackerState().isEmpty())

    val file = projectRoot.findChild(fileName)!!
    val document = runReadAction { FileDocumentManager.getInstance().getDocument(file)!! }

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.randomAlphanumeric(100)) } }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus(' ', 'M', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus(' ', 'M', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }
  }

  fun `test staged`() {
    val fileName = "file.txt"
    Executor.touch(fileName, RandomStringUtils.randomAlphanumeric(200))
    git("add .")
    git("commit -m file")

    runWithTrackerUpdate("refresh") {
      refresh()
    }
    assertTrue(trackerState().isEmpty())

    val file = projectRoot.findChild(fileName)!!
    val indexFile = project.service<GitIndexFileSystemRefresher>().getFile(projectRoot, VcsUtil.getFilePath(file))!!
    val document = runReadAction { FileDocumentManager.getInstance().getDocument(indexFile)!!}

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.randomAlphanumeric(100)) } }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus('M', ' ', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus('M', 'M', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }
  }

  fun `test untracked`() {
    val fileName = "file.txt"
    val file = runWithTrackerUpdate("createChildData") {
      invokeAndWaitIfNeeded { runWriteAction { projectRoot.createChildData(this, fileName) }}
    }
    TestCase.assertEquals(GitFileStatus('?', '?', VcsUtil.getFilePath(file)),
                          trackerState().statuses.getValue(VcsUtil.getFilePath(projectRoot, fileName)))

    val document = runReadAction { FileDocumentManager.getInstance().getDocument(file)!!}

    runWithTrackerUpdate("setText") {
      invokeAndWaitIfNeeded { runWriteAction { document.setText(RandomStringUtils.randomAlphanumeric(100)) } }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus('?', '?', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }

    runWithTrackerUpdate("saveDocument") {
      invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
    }
    trackerState().let { state ->
      TestCase.assertEquals(GitFileStatus('?', '?', VcsUtil.getFilePath(file)),
                            state.statuses.getValue(VcsUtil.getFilePath(file)))
    }
  }

  private fun trackerState() = tracker.state.rootStates.getValue(projectRoot)

  private fun <T> runWithTrackerUpdate(name: String, function: () -> T): T {
    return tracker.futureUpdate(name).let { futureUpdate ->
      val result = function()
      changeListManager.waitEverythingDoneInTestMode()
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
    val removeListener = Disposer.newDisposable("Listener disposable")
    val future = SettableFuture.create<Unit>()
    addListener(object : GitStageTrackerListener {
      override fun update() {
        UsefulTestCase.LOG.debug("Refreshed tracker after \"$name\"")
        future.set(Unit)
      }
    }, removeListener)
    future.addListener(Runnable { Disposer.dispose(removeListener) }, MoreExecutors.directExecutor())
    return future
  }
}