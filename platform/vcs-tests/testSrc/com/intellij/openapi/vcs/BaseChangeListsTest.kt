// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.common.runAll
import com.intellij.util.io.createDirectories
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Paths
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

abstract class BaseChangeListsTest : LightPlatformTestCase() {
  companion object {
    val DEFAULT = LocalChangeList.getDefaultName()

    fun createMockFileEditor(document: Document): FileEditor {
      val editor = mock<FileEditor>(extraInterfaces = arrayOf(DocumentReferenceProvider::class))
      val references = listOf(DocumentReferenceManager.getInstance().create(document))
      whenever((editor as DocumentReferenceProvider).documentReferences).thenReturn(references)
      return editor
    }
  }

  protected lateinit var vcs: MyMockVcs
  protected lateinit var changeProvider: MyMockChangeProvider

  protected lateinit var clm: ChangeListManagerImpl
  protected lateinit var dirtyScopeManager: VcsDirtyScopeManagerImpl

  protected lateinit var testRoot: VirtualFile

  private lateinit var vcsManager: ProjectLevelVcsManagerImpl

  protected var arePartialChangelistsSupported: Boolean = true

  override fun setUp() {
    super.setUp()
    val project = project
    val testRootPath = Paths.get(project.basePath!!).resolve(getTestName(true)).createDirectories()
    testRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(testRootPath)!!

    vcs = MyMockVcs(project)
    changeProvider = MyMockChangeProvider()
    vcs.changeProvider = changeProvider

    clm = ChangeListManagerImpl.getInstanceImpl(project)
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project) as VcsDirtyScopeManagerImpl

    vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
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
    runAll(
      { resetSettings() },
      { resetChanges() },
      { resetChangelists() },
      { vcsManager.directoryMappings = emptyList() },
      { project.getServiceIfCreated(AllVcsesI::class.java)?.unregisterManually(vcs) },
      { runWriteAction { testRoot.delete(this) } },
      { super.tearDown() }
    )
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
    clm.addChangeList(LocalChangeList.getDefaultName(), null)
    clm.setDefaultChangeList(LocalChangeList.getDefaultName())
    for (changeListName in clm.changeLists.map { it.name }) {
      if (changeListName != LocalChangeList.getDefaultName()) clm.removeChangeList(changeListName)
    }
    clm.waitUntilRefreshed()
  }


  protected fun addLocalFile(name: String, content: String, baseContent: String? = null): VirtualFile {
    val file = createLocalFile(name, content)
    assertFalse(changeProvider.files.contains(file))
    changeProvider.files.add(file)

    if (baseContent != null) {
      setBaseVersion(name, baseContent)
    }

    return file
  }

  protected fun createLocalFile(name: String, content: String): VirtualFile {
    return runWriteAction {
      val file = testRoot.createChildData(this, name)
      VfsUtil.saveText(file, parseInput(content))
      file
    }
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
    clm.waitUntilRefreshed()
    UIUtil.dispatchAllInvocationEvents() // ensure `fileStatusesChanged` events are fired
  }


  protected val String.toFilePath: FilePath get() = VcsUtil.getFilePath(testRoot, this)
  protected fun Array<out String>.toFilePaths() = this.asList().toFilePaths()
  private fun List<String>.toFilePaths() = this.map { it.toFilePath }
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
    project.messageBus.syncPublisher(VcsFreezingProcess.Listener.TOPIC).onFreeze()
    try {
      task()
    }
    finally {
      project.messageBus.syncPublisher(VcsFreezingProcess.Listener.TOPIC).onUnfreeze()
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

    override fun getChanges(
      dirtyScope: VcsDirtyScope,
      builder: ChangelistBuilder,
      progress: ProgressIndicator,
      addGate: ChangeListManagerGate,
    ) {
      markerSemaphore.release()
      semaphore.acquireOrThrow()
      try {
        val changesToProcess = changes.map { (filePath, beforeRevision) ->
          val afterContent: ContentRevision? =
            if (files.find { VcsUtil.getFilePath(it) == filePath } == null)
              null
            else CurrentContentRevision(filePath)
          Change(beforeRevision, afterContent)
        }

        changesToProcess.forEach { change -> builder.processChange(change, MockAbstractVcs.getKey()) }

        for (file in files) {
          val path = VcsUtil.getFilePath(file)
          if (changesToProcess.none { ChangesUtil.matches(it, path) }) {
            builder.processUnversionedFile(path)
          }
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