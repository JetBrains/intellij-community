// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@TestApplication
@RunInEdt(writeIntent = true)
class NoIteratingUnderExcludedRootsTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private val testDisposable
    get() = projectModel.disposableRule.disposable

  private lateinit var moduleRoot: VirtualFile
  private lateinit var excludedDir: VirtualFile
  private lateinit var txtFile: VirtualFile

  @BeforeEach
  fun setUp() {
    moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("myModuleRoot")
    excludedDir = VfsTestUtil.createDir(moduleRoot, "excluded")
    txtFile = VfsTestUtil.createFile(excludedDir, "my.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, moduleRoot)
  }

  @Test
  fun testRegularExclude() {
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot)
  }

  @Test
  fun testExcludeOnCreateEvent() {
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot)

    val nioExcluded2 = moduleRoot.toNioPath().resolve("excluded2")
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      return@AsyncFileListener object : ChangeApplier {
        override fun afterVfsChange() {
          excludeIfCreateEvent(events, nioExcluded2)
        }
      }
    }, testDisposable)

    FileUtil.writeToFile(nioExcluded2.resolve("my.txt").toFile(), "")
    moduleRoot.refresh(false, true)
    checkIterate(moduleRoot, moduleRoot)
  }

  @Test
  fun testExcludeOnEarlyCreateEvent() {
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot)

    val nioExcluded2 = moduleRoot.toNioPath().resolve("excluded2")
    listenEarlyAfterVfsChanges(object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        excludeIfCreateEvent(events, nioExcluded2)
      }
    })
    FileUtil.writeToFile(nioExcluded2.resolve("my.txt").toFile(), "")
    moduleRoot.refresh(false, true)
    checkIterate(moduleRoot, moduleRoot)
  }

  private fun excludeIfCreateEvent(events: List<VFileEvent>, createdPathToExclude: Path) {
    for (event in events) {
      if (event is VFileCreateEvent) {
        val file = event.file
        if (file != null && file.toNioPath() == createdPathToExclude) {
          excludeFolder(file)
        }
      }
    }
  }

  @Test
  fun testExcludeOnRenameEvent() {
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot)

    val oldExcludeName = excludedDir.name
    val newExcludeName = "$oldExcludeName-2"
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      return@AsyncFileListener object : ChangeApplier {
        override fun afterVfsChange() {
          excludeIfRenameEvent(events, excludedDir.parent, newExcludeName)
        }
      }
    }, testDisposable)
    WriteAction.run<Throwable> {
      excludedDir.rename(null, newExcludeName)
    }
    checkIterate(moduleRoot, moduleRoot)
  }

  @Test
  fun testExcludeOnEarlyRenameEvent() {
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot)

    val oldExcludeName = excludedDir.name
    val newExcludeName = "$oldExcludeName-2"
    listenEarlyAfterVfsChanges(object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        excludeIfRenameEvent(events, excludedDir.parent, newExcludeName)
      }
    })
    WriteAction.run<Throwable> {
      excludedDir.rename(null, newExcludeName)
    }
    checkIterate(moduleRoot, moduleRoot)
  }

  private fun excludeIfRenameEvent(events: List<VFileEvent>, parent: VirtualFile, newChildName: String) {
    for (event in events) {
      if (event is VFilePropertyChangeEvent) {
        val file = event.file
        if (file.parent == parent && event.newValue == newChildName) {
          excludeFolder(file)
        }
      }
    }
  }

  @Test
  fun testExcludeOnMoveEvent() {
    val fooDir = VfsTestUtil.createDir(moduleRoot, "foo")
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot, fooDir)

    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      return@AsyncFileListener object : ChangeApplier {
        override fun afterVfsChange() {
          excludeIfMoveEvent(events, fooDir, excludedDir.name)
        }
      }
    }, testDisposable)
    WriteAction.run<Throwable> {
      excludedDir.move(null, fooDir)
    }
    checkIterate(moduleRoot, moduleRoot, fooDir)
  }

  @Test
  fun testExcludeOnEarlyMoveEvent() {
    val fooDir = VfsTestUtil.createDir(moduleRoot, "foo")
    excludeFolder(excludedDir)
    checkIterate(moduleRoot, moduleRoot, fooDir)
    listenEarlyAfterVfsChanges(object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        excludeIfMoveEvent(events, fooDir, excludedDir.name)
      }
    })
    WriteAction.run<Throwable> {
      excludedDir.move(null, fooDir)
    }
    checkIterate(moduleRoot, moduleRoot, fooDir)
  }

  private fun excludeIfMoveEvent(events: List<VFileEvent>, newParent: VirtualFile, fileName: String) {
    for (event in events) {
      if (event is VFileMoveEvent) {
        val file = event.file
        if (file.parent == newParent && file.name == fileName) {
          excludeFolder(file)
        }
      }
    }
  }

  private fun excludeFolder(folderToExclude: VirtualFile) {
    WriteAction.run<Throwable> {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      workspaceModel.updateProjectModel("exclude") {
        val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
        it.addEntity(ExcludeUrlEntity(folderToExclude.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
      }
    }
  }

  private fun checkIterate(file: VirtualFile, vararg expectToIterate: VirtualFile) {
    val collected: MutableList<VirtualFile> = ArrayList()
    fileIndex.iterateContentUnderDirectory(file) { collected.add(it) }
    UsefulTestCase.assertSameElements(collected, *expectToIterate)
  }

  private fun listenEarlyAfterVfsChanges(fileListener: BulkFileListener) {
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      val processed = ConcurrentHashMap.newKeySet<VFileEvent>()
      val earlyVfsEventProcessor = EarlyVfsEventProcessor(testDisposable) {
        processed.add(it)
        fileListener.after(listOf(it))
      }
      val result = object : ChangeApplier {
        override fun beforeVfsChange() {
          fileListener.before(events)
        }

        override fun afterVfsChange() {
          fileListener.after(events.filter { !processed.contains(it) })
          Disposer.dispose(earlyVfsEventProcessor)
        }
      }
      for (event in events) {
        earlyVfsEventProcessor.processIfPossible(event)
      }
      return@AsyncFileListener result
    }, testDisposable)
  }
}

private class EarlyVfsEventProcessor(parentDisposable: Disposable,
                                     private val afterVfsChangeProcessor: (VFileEvent) -> Unit): Disposable {
  init {
    Disposer.register(parentDisposable, this)
  }

  fun processIfPossible(event: VFileEvent) {
    when (event) {
      is VFileCreateEvent -> {
        processWhenAvailable(event.parent, event.childName) {
          afterVfsChangeProcessor(event)
        }
      }
      is VFilePropertyChangeEvent -> {
        val parent = event.file.parent
        val newName = event.newValue as? String
        if (parent != null && newName != null) {
          processWhenAvailable(parent, newName) {
            afterVfsChangeProcessor(event)
          }
        }
      }
      is VFileMoveEvent -> {
        processWhenAvailable(event.newParent, event.file.name) {
          afterVfsChangeProcessor(event)
        }
      }
    }
  }

  private fun processWhenAvailable(parent: VirtualFile, newChildName: String, processor: () -> Unit) {
    val disposable = Disposer.newDisposable(this)
    val url = parent.url + "/" + newChildName
    VirtualFilePointerManager.getInstance().create(url, disposable, object : VirtualFilePointerListener {
      override fun validityChanged(pointers: Array<VirtualFilePointer>) {
        if (!parent.isValid) {
          Disposer.dispose(disposable)
          return
        }
        parent.findChild(newChildName)?.let {
          Disposer.dispose(disposable)
          processor()
        }
      }
    })
  }

  override fun dispose() {}
}
