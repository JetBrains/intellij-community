// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BaseLineStatusTrackerTestCase.Companion.parseInput
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import java.lang.IllegalStateException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

abstract class BaseChangeListsTest : LightPlatformTestCase() {
  protected lateinit var vcs: MyMockVcs
  protected lateinit var changeProvider: MyMockChangeProvider

  protected lateinit var clm: ChangeListManagerImpl
  protected lateinit var dirtyScopeManager: VcsDirtyScopeManagerImpl

  protected lateinit var testRoot: VirtualFile

  protected lateinit var vcsManager: ProjectLevelVcsManagerImpl

  protected var arePartialChangelistsSupported: Boolean = true

  override fun setUp() {
    super.setUp()
    testRoot = runWriteAction {
      VfsUtil.markDirtyAndRefresh(false, false, true, ourProject.baseDir)
      VfsUtil.createDirectoryIfMissing(ourProject.baseDir, getTestName(true))
    }

    vcs = MyMockVcs(ourProject)
    changeProvider = MyMockChangeProvider()
    vcs.changeProvider = changeProvider

    clm = ChangeListManagerImpl.getInstanceImpl(ourProject)
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(ourProject) as VcsDirtyScopeManagerImpl

    vcsManager = ProjectLevelVcsManager.getInstance(ourProject) as ProjectLevelVcsManagerImpl
    vcsManager.registerVcs(vcs)
    vcsManager.directoryMappings = listOf(VcsDirectoryMapping(testRoot.path, vcs.name))
    vcsManager.waitForInitialized()
    assertTrue(vcsManager.hasActiveVcss())

    try {
      resetTestState()
    }
    catch (e: Throwable) {
      super.tearDown()
      throw e
    }
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { resetSettings() })
      .append(ThrowableRunnable { resetChanges() })
      .append(ThrowableRunnable { resetChangelists() })
      .append(ThrowableRunnable { vcsManager.directoryMappings = emptyList() })
      .append(ThrowableRunnable { AllVcses.getInstance(ourProject).unregisterManually(vcs) })
      .append(ThrowableRunnable { runWriteAction { testRoot.delete(this) } })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  protected open fun resetSettings() {
    arePartialChangelistsSupported = false
  }

  protected open fun resetTestState() {
    resetSettings()
    resetChanges()
    resetChangelists()
    resetTestRootContent()
  }

  private fun resetTestRootContent() {
    VfsUtil.markDirtyAndRefresh(false, true, true, testRoot)
    runWriteAction { testRoot.children.forEach { child -> child.delete(this) } }
  }

  private fun resetChanges() {
    changeProvider.changes.clear()
    changeProvider.files.clear()
    clm.waitUntilRefreshed()
  }

  private fun resetChangelists() {
    clm.addChangeList(LocalChangeList.DEFAULT_NAME, null)
    clm.setDefaultChangeList(LocalChangeList.DEFAULT_NAME)
    for (changeListName in clm.changeLists.map { it.name }) {
      if (changeListName != LocalChangeList.DEFAULT_NAME) clm.removeChangeList(changeListName)
    }
    clm.waitUntilRefreshed()
  }


  protected fun addLocalFile(name: String, content: String): VirtualFile {
    val file = runWriteAction {
      val file = testRoot.createChildData(this, name)
      VfsUtil.saveText(file, parseInput(content))
      file
    }

    assertFalse(changeProvider.files.contains(file))
    changeProvider.files.add(file)
    return file
  }

  protected fun removeLocalFile(name: String) {
    val file = runWriteAction {
      val file = VfsUtil.findRelativeFile(testRoot, name)
      file!!.delete(this)
      file
    }

    assertTrue(changeProvider.files.contains(file))
    changeProvider.files.remove(file)
  }

  protected fun setBaseVersion(name: String, baseContent: String?) {
    setBaseVersion(name, baseContent, name)
  }

  protected fun setBaseVersion(name: String, baseContent: String?, oldName: String) {
    val contentRevision: ContentRevision? = when (baseContent) {
      null -> null
      else -> SimpleContentRevision(parseInput(baseContent), oldName.toFilePath, baseContent)
    }

    changeProvider.changes[name.toFilePath] = contentRevision
  }

  protected fun removeBaseVersion(name: String) {
    changeProvider.changes.remove(name.toFilePath)
  }

  protected fun refreshCLM() {
    dirtyScopeManager.markEverythingDirty()
    clm.scheduleUpdate()
    clm.waitUntilRefreshed()
    UIUtil.dispatchAllInvocationEvents() // ensure `fileStatusesChanged` events are fired
  }


  protected val String.toFilePath: FilePath get() = VcsUtil.getFilePath(testRoot, this)
  protected val VirtualFile.change: Change? get() = clm.getChange(this)
  protected val VirtualFile.document: Document get() = FileDocumentManager.getInstance().getDocument(this)!!

  protected fun VirtualFile.assertAffectedChangeLists(vararg expectedNames: String) {
    assertSameElements(clm.getChangeLists(this).map { it.name }, *expectedNames)
  }

  protected fun FilePath.assertAffectedChangeLists(vararg expectedNames: String) {
    val change = clm.getChange(this)!!
    assertSameElements(clm.getChangeLists(change).map { it.name }, *expectedNames)
  }


  protected fun String.asListNameToList(): LocalChangeList = clm.changeLists.find { it.name == this }!!
  protected fun String.asListIdToList(): LocalChangeList = clm.changeLists.find { it.id == this }!!
  protected fun String.asListNameToId(): String = asListNameToList().id
  protected fun String.asListIdToName(): String = asListIdToList().name
  protected fun Iterable<String>.asListNamesToIds() = this.map { it.asListNameToId() }
  protected fun Iterable<String>.asListIdsToNames() = this.map { it.asListIdToName() }
  private fun changeListsNames() = clm.changeLists.map { it.name }


  fun runBatchFileChangeOperation(task: () -> Unit) {
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(ourProject, "Update")
    try {
      task()
    }
    finally {
      BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(ourProject)
    }
  }

  protected fun createChangelist(listName: String) {
    assertDoesntContain(changeListsNames(), listName)
    clm.addChangeList(listName, null)
  }

  protected fun removeChangeList(listName: String) {
    assertContainsElements(changeListsNames(), listName)
    clm.removeChangeList(listName)
  }

  protected fun setDefaultChangeList(listName: String) {
    assertContainsElements(changeListsNames(), listName)
    clm.setDefaultChangeList(listName)
  }

  protected fun VirtualFile.moveChanges(fromListName: String, toListName: String) {
    assertContainsElements(changeListsNames(), fromListName)
    assertContainsElements(changeListsNames(), toListName)
    val listChange = fromListName.asListNameToList().changes.find { it == this.change!! }!!
    clm.moveChangesTo(toListName.asListNameToList(), listChange)
  }

  protected fun VirtualFile.moveAllChangesTo(toListName: String) {
    assertContainsElements(changeListsNames(), toListName)
    clm.moveChangesTo(toListName.asListNameToList(), this.change!!)
  }


  protected inner class MyMockChangeProvider : ChangeProvider {
    private val semaphore = Semaphore(1)
    private val markerSemaphore = Semaphore(0)

    val changes = mutableMapOf<FilePath, ContentRevision?>()
    val files = mutableSetOf<VirtualFile>()

    override fun getChanges(dirtyScope: VcsDirtyScope,
                            builder: ChangelistBuilder,
                            progress: ProgressIndicator,
                            addGate: ChangeListManagerGate) {
      markerSemaphore.release()
      semaphore.acquireOrThrow()
      try {
        for ((filePath, beforeRevision) in changes) {
          val file = files.find { VcsUtil.getFilePath(it) == filePath }
          val afterContent: ContentRevision? = when (file) {
            null -> null
            else -> CurrentContentRevision(filePath)
          }

          val change = Change(beforeRevision, afterContent)

          builder.processChange(change, MockAbstractVcs.getKey())
        }
      }
      finally {
        semaphore.release()
        markerSemaphore.acquireOrThrow()
      }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean {
      return false
    }

    override fun doCleanup(files: List<VirtualFile>) {
    }

    fun awaitAndBlockRefresh(): AccessToken {
      semaphore.acquireOrThrow()

      dirtyScopeManager.markEverythingDirty()
      clm.scheduleUpdate()

      markerSemaphore.acquireOrThrow()
      markerSemaphore.release()

      return object : AccessToken() {
        override fun finish() {
          semaphore.release()
        }
      }
    }

    private fun Semaphore.acquireOrThrow() {
      val success = this.tryAcquire(10000, TimeUnit.MILLISECONDS)
      if (!success) throw IllegalStateException()
    }
  }

  protected inner class MyMockVcs(project: Project) : MockAbstractVcs(project) {
    override fun arePartialChangelistsSupported(): Boolean = arePartialChangelistsSupported
  }
}