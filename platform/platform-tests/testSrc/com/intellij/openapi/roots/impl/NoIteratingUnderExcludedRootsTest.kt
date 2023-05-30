// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.toVirtualFileUrl
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@TestApplication
@RunInEdt
class NoIteratingUnderExcludedRootsTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private val testDisposable
    get() = projectModel.disposableRule.disposable

  @Test
  fun testRegularExclude() {
    val data = initBasicProject()
    excludeFolder(data.excluded)
    checkIterate(data.moduleRoot, data.moduleRoot)
  }

  @Test
  fun testExcludeOnCreateEvent() {
    val data = initBasicProject()
    excludeFolder(data.excluded)
    checkIterate(data.moduleRoot, data.moduleRoot)

    val nioExcluded2 = data.moduleRoot.toNioPath().resolve("excluded2")
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      return@AsyncFileListener object : ChangeApplier {
        override fun afterVfsChange() {
          excludeIfCreateEvent(events, nioExcluded2)
        }
      }
    }, testDisposable)

    FileUtil.writeToFile(nioExcluded2.resolve("my.txt").toFile(), "")
    data.moduleRoot.refresh(false, true)
    checkIterate(data.moduleRoot, data.moduleRoot)
  }

  @Test
  fun testExcludeOnEarlyCreateEvent() {
    val data = initBasicProject()
    excludeFolder(data.excluded)
    checkIterate(data.moduleRoot, data.moduleRoot)

    val nioExcluded2 = data.moduleRoot.toNioPath().resolve("excluded2")
    listenEarlyAfterVfsChanges(object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        excludeIfCreateEvent(events, nioExcluded2)
      }
    })
    FileUtil.writeToFile(nioExcluded2.resolve("my.txt").toFile(), "")
    data.moduleRoot.refresh(false, true)
    checkIterate(data.moduleRoot, data.moduleRoot)
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
    val data = initBasicProject()
    excludeFolder(data.excluded)
    checkIterate(data.moduleRoot, data.moduleRoot)

    val oldExcludeName = data.excluded.name
    val newExcludeName = "$oldExcludeName-2"
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      return@AsyncFileListener object : ChangeApplier {
        override fun afterVfsChange() {
          excludeIfRenameEvent(events, data.excluded.parent, newExcludeName)
        }
      }
    }, testDisposable)
    WriteAction.run<Throwable> {
      data.excluded.rename(null, newExcludeName)
    }
    checkIterate(data.moduleRoot, data.moduleRoot)
  }

  @Test
  fun testExcludeOnEarlyRenameEvent() {
    val data = initBasicProject()
    excludeFolder(data.excluded)
    checkIterate(data.moduleRoot, data.moduleRoot)

    val oldExcludeName = data.excluded.name
    val newExcludeName = "$oldExcludeName-2"
    listenEarlyAfterVfsChanges(object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        excludeIfRenameEvent(events, data.excluded.parent, newExcludeName)
      }
    })
    WriteAction.run<Throwable> {
      data.excluded.rename(null, newExcludeName)
    }
    checkIterate(data.moduleRoot, data.moduleRoot)
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

  private fun initBasicProject(): Data {
    return Data().also {
      val module = projectModel.createModule()
      checkIterate(it.moduleRoot)
      PsiTestUtil.addContentRoot(module, it.moduleRoot)
      checkIterate(it.moduleRoot, it.moduleRoot, it.excluded, it.txt)
    }
  }

  private inner class Data {
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("myModuleRoot")
    val excluded = VfsTestUtil.createDir(moduleRoot, "excluded")
    val txt = VfsTestUtil.createDir(excluded, "my.txt")
  }

  private fun excludeFolder(folderToExclude: VirtualFile) {
    WriteAction.run<Throwable> {
      WorkspaceModel.getInstance(projectModel.project).updateProjectModel("exclude") {
        val virtualFileUrlManager = VirtualFileUrlManager.getInstance(projectModel.project)
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
    if (event is VFileCreateEvent) {
      processWhenAvailable(event.parent, event.childName) {
        afterVfsChangeProcessor(event)
      }
    }
    if (event is VFilePropertyChangeEvent) {
      val parent = event.file.parent
      val newName = event.newValue as? String
      if (parent != null && newName != null) {
        processWhenAvailable(parent, newName) {
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
