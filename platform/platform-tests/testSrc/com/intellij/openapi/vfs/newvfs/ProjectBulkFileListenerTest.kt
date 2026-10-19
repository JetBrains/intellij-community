// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.ProjectBulkFileListenerRegistry
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class ProjectBulkFileListenerTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `runtime listener receives project-scoped events`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val afterEvents = AtomicReference<List<VFileEvent>>()
    val callbackProject = AtomicReference<Project>()
    val afterCallback = CountDownLatch(1)

    ProjectBulkFileListenerRegistry.getInstance(projectModel.project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        callbackProject.set(project)
        afterEvents.set(events)
        afterCallback.countDown()
      }
    }, disposable)

    val created = WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildData(this, "created.txt")
    }

    assertTrue(afterCallback.await(10, TimeUnit.SECONDS))
    assertSame(projectModel.project, callbackProject.get())
    val afterCreate = afterEvents.get().filterIsInstance<VFileCreateEvent>().single()
    assertSame(root, afterCreate.parent)
    assertEquals(created, afterCreate.file)
  }

  @Test
  fun `the after callback receives an empty list for nonmatching events`(@TestDisposable disposable: Disposable) {
    addContentRoot()
    val afterEvents = AtomicReference<List<VFileEvent>>()
    val afterCallback = CountDownLatch(1)
    val outside = projectModel.baseProjectDir.newVirtualDirectory("outside")

    ProjectBulkFileListenerRegistry.getInstance(projectModel.project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        afterEvents.set(events)
        afterCallback.countDown()
      }
    }, disposable)

    WriteAction.run<IOException> {
      outside.createChildData(this, "outside.txt")
    }

    assertTrue(afterCallback.await(10, TimeUnit.SECONDS))
    assertTrue(afterEvents.get().isEmpty())
  }

  @Test
  fun `move out reports a delete`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val outside = projectModel.baseProjectDir.newVirtualDirectory("move-outside")
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildData(this, "moved-out.txt")
    }
    val observedChange = observeNextChange(disposable)
    val requestor = Any()

    WriteAction.run<IOException> {
      file.move(requestor, outside)
    }

    observedChange.awaitAfterCallback()
    val afterDelete = observedChange.afterEvents.get().filterIsInstance<VFileDeleteEvent>().single()
    assertSame(requestor, afterDelete.requestor)
    assertSame(file, afterDelete.file)
    assertTrue(observedChange.afterEvents.get().none { it is VFileMoveEvent })
  }

  @Test
  fun `delete reports cached descendants`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    lateinit var deletedDirectory: VirtualFile
    lateinit var child: VirtualFile
    lateinit var nestedDirectory: VirtualFile
    lateinit var nestedChild: VirtualFile
    lateinit var uncachedChildPath: java.nio.file.Path
    WriteAction.run<IOException> {
      deletedDirectory = root.createChildDirectory(this, "deleted-tree")
      child = deletedDirectory.createChildData(this, "child.txt")
      nestedDirectory = deletedDirectory.createChildDirectory(this, "nested")
      nestedChild = nestedDirectory.createChildData(this, "nested.txt")
      uncachedChildPath = nestedDirectory.toNioPath().resolve("uncached.txt")
      Files.writeString(uncachedChildPath, "uncached")
    }
    val observedChange = observeNextChange(disposable)

    WriteAction.run<IOException> {
      deletedDirectory.delete(this)
    }

    observedChange.awaitAfterCallback()
    val expectedPaths = setOf(deletedDirectory, child, nestedDirectory, nestedChild).map { it.path }.toSet()
    val afterDeletes = observedChange.afterEvents.get().filterIsInstance<VFileDeleteEvent>()
    assertEquals(expectedPaths, afterDeletes.map { it.path }.toSet())
    assertTrue(afterDeletes.none { it.path == uncachedChildPath.toString() })
    for (path in expectedPaths) {
      val afterDelete = afterDeletes.single { it.path == path }
      assertSame(this, afterDelete.requestor)
    }
  }

  @Test
  fun `move in reports creates for the cached tree`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val outside = projectModel.baseProjectDir.newVirtualDirectory("move-in-outside")
    lateinit var movedDirectory: VirtualFile
    lateinit var child: VirtualFile
    lateinit var nestedDirectory: VirtualFile
    lateinit var nestedChild: VirtualFile
    WriteAction.run<IOException> {
      movedDirectory = outside.createChildDirectory(this, "moved-tree")
      child = movedDirectory.createChildData(this, "child.txt")
      nestedDirectory = movedDirectory.createChildDirectory(this, "nested")
      nestedChild = nestedDirectory.createChildData(this, "nested.txt")
    }
    val observedChange = observeNextChange(disposable)

    WriteAction.run<IOException> {
      movedDirectory.move(this, root)
    }

    observedChange.awaitAfterCallback()
    assertTrue(observedChange.afterEvents.get().none { it is VFileMoveEvent })
    val createEvents = observedChange.afterEvents.get().filterIsInstance<VFileCreateEvent>()
    val expectedFiles = setOf(movedDirectory, child, nestedDirectory, nestedChild)
    assertEquals(expectedFiles.map { it.path }.toSet(), createEvents.map { it.path }.toSet())
    for (expectedFile in expectedFiles) {
      val createEvent = createEvents.single { it.path == expectedFile.path }
      assertSame(expectedFile.parent, createEvent.parent)
      assertEquals(expectedFile, createEvent.file)
    }
  }

  @Test
  fun `copy in reports copy events for the cached tree`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val outside = projectModel.baseProjectDir.newVirtualDirectory("copy-in-outside")
    lateinit var sourceDirectory: VirtualFile
    lateinit var sourceChild: VirtualFile
    lateinit var sourceNestedDirectory: VirtualFile
    lateinit var sourceNestedChild: VirtualFile
    WriteAction.run<IOException> {
      sourceDirectory = outside.createChildDirectory(this, "source-tree")
      sourceChild = sourceDirectory.createChildData(this, "child.txt")
      sourceNestedDirectory = sourceDirectory.createChildDirectory(this, "nested")
      sourceNestedChild = sourceNestedDirectory.createChildData(this, "nested.txt")
    }
    val observedChange = observeNextChange(disposable)
    val requestor = Any()

    val copiedDirectory = WriteAction.computeAndWait<VirtualFile, IOException> {
      (PersistentFS.getInstance() as PersistentFSImpl).copyFile(requestor, sourceDirectory, root, "copied-tree")
    }

    observedChange.awaitAfterCallback()
    val afterCopy = observedChange.afterEvents.get().filterIsInstance<VFileCopyEvent>().single { it.newParent == root }
    assertSame(sourceDirectory, afterCopy.file)
    assertEquals("copied-tree", afterCopy.newChildName)
    assertSame(requestor, afterCopy.requestor)

    val copiedRoot = copiedDirectory as NewVirtualFile
    val copiedChild = checkNotNull(copiedRoot.findChildIfCached("child.txt"))
    val copiedNestedDirectory = checkNotNull(copiedRoot.findChildIfCached("nested"))
    val copiedNestedChild = checkNotNull(copiedNestedDirectory.findChildIfCached("nested.txt"))
    val expectedSourceByDestination = mapOf(
      copiedRoot to sourceDirectory,
      copiedChild to sourceChild,
      copiedNestedDirectory to sourceNestedDirectory,
      copiedNestedChild to sourceNestedChild,
    )
    val rootChildInfos = checkNotNull(afterCopy.children).associateBy { it.name.toString() }
    val nestedInfo = rootChildInfos.getValue("nested")
    val expectedInfoByDestination = mapOf(
      copiedChild to rootChildInfos.getValue("child.txt"),
      copiedNestedDirectory to nestedInfo,
      copiedNestedChild to checkNotNull(nestedInfo.children).single(),
    )
    val afterEvents = observedChange.afterEvents.get()
    val copyEventsByPath = afterEvents.filterIsInstance<VFileCopyEvent>().associateBy { it.path }
    assertEquals(expectedSourceByDestination.keys.map { it.path }.toSet(), copyEventsByPath.keys)
    assertSame(afterCopy, copyEventsByPath.getValue(copiedRoot.path))
    for ((destination, source) in expectedSourceByDestination) {
      val copyEvent = copyEventsByPath.getValue(destination.path)
      assertEquals(source, copyEvent.file)
      assertSame(destination.parent, copyEvent.newParent)
      assertEquals(destination, copyEvent.findCreatedFile())
      assertSame(requestor, copyEvent.requestor)
      expectedInfoByDestination[destination]?.let { childInfo ->
        assertSame(childInfo.fileAttributes, copyEvent.attributes)
        assertEquals(childInfo.symlinkTarget, copyEvent.symlinkTarget)
        assertSame(childInfo.children, copyEvent.children)
        assertEquals(childInfo.isAllChildren, copyEvent.isAllChildren)
      }
    }
    assertTrue(afterEvents.none { it is VFileCreateEvent })
  }

  @Test
  fun `the after callback keeps a move and creates events for its cached children`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    lateinit var source: VirtualFile
    lateinit var destination: VirtualFile
    lateinit var movedDirectory: VirtualFile
    lateinit var child: VirtualFile
    WriteAction.run<IOException> {
      source = root.createChildDirectory(this, "source")
      destination = root.createChildDirectory(this, "destination")
      movedDirectory = source.createChildDirectory(this, "moved-within")
      child = movedDirectory.createChildData(this, "child.txt")
    }
    val observedChange = observeNextChange(disposable)
    val requestor = Any()

    WriteAction.run<IOException> {
      movedDirectory.move(requestor, destination)
    }

    observedChange.awaitAfterCallback()
    val afterEvents = observedChange.afterEvents.get()
    assertEquals(2, afterEvents.size)
    val afterMove = afterEvents.first() as VFileMoveEvent
    assertSame(movedDirectory, afterMove.file)
    assertSame(source, afterMove.oldParent)
    assertSame(destination, afterMove.newParent)
    assertSame(requestor, afterMove.requestor)

    val childCreate = afterEvents.last() as VFileCreateEvent
    assertEquals(child, childCreate.file)
    assertEquals(child.path, childCreate.path)
    assertSame(child.parent, childCreate.parent)
    assertSame(requestor, childCreate.requestor)
  }

  @Test
  fun `the after callback keeps a rename and creates events for its cached children`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot("*.excluded")
    lateinit var directory: VirtualFile
    lateinit var nested: VirtualFile
    lateinit var child: VirtualFile
    WriteAction.run<IOException> {
      directory = root.createChildDirectory(this, "tree")
      nested = directory.createChildDirectory(this, "nested")
      child = nested.createChildData(this, "child.txt")
      directory.createChildData(this, "omitted.excluded")
    }
    val observedChange = observeNextChange(disposable)
    val requestor = Any()

    WriteAction.run<IOException> {
      directory.rename(requestor, "renamed")
    }

    observedChange.awaitAfterCallback()
    val afterEvents = observedChange.afterEvents.get()
    assertEquals(3, afterEvents.size)
    val afterRename = afterEvents.first() as VFilePropertyChangeEvent
    assertTrue(afterRename.isRename)
    assertSame(directory, afterRename.file)
    assertEquals("tree", afterRename.oldValue)
    assertEquals("renamed", afterRename.newValue)
    assertSame(requestor, afterRename.requestor)

    val childEvents = afterEvents.drop(1).map { it as VFileCreateEvent }
    assertEquals(setOf(nested, child), childEvents.map { it.file }.toSet())
    for (childEvent in childEvents) {
      assertSame(childEvent.file?.parent, childEvent.parent)
      assertEquals(childEvent.file?.path, childEvent.path)
      assertSame(requestor, childEvent.requestor)
    }
  }

  @Test
  fun `rename within the project reports one rename event`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildData(this, "renamed.txt")
    }
    val observedChange = observeNextChange(disposable)

    WriteAction.run<IOException> {
      file.rename(this, "new-name.txt")
    }

    observedChange.awaitAfterCallback()
    val afterRenames = observedChange.afterEvents.get().filterIsInstance<VFilePropertyChangeEvent>().filter { it.isRename }
    assertEquals(1, afterRenames.size)
    assertSame(file, afterRenames.single().file)
    assertEquals("renamed.txt", afterRenames.single().oldValue)
    assertEquals("new-name.txt", afterRenames.single().newValue)
  }

  @Test
  fun `rename that becomes indexable appears as create after`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot("*.excluded")
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildData(this, "renamed.excluded")
    }
    val observedChange = observeNextChange(disposable)

    WriteAction.run<IOException> {
      file.rename(this, "renamed.txt")
    }

    observedChange.awaitAfterCallback()
    assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isIndexable(file))
    val afterCreate = observedChange.afterEvents.get().filterIsInstance<VFileCreateEvent>().single()
    assertEquals(file, afterCreate.file)
    assertTrue(observedChange.afterEvents.get().none { it is VFilePropertyChangeEvent && it.isRename })
  }

  @Test
  fun `rename that becomes excluded appears as delete after`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot("*.excluded")
    val file = WriteAction.computeAndWait<VirtualFile, IOException> {
      root.createChildData(this, "renamed.txt")
    }
    val observedChange = observeNextChange(disposable)

    WriteAction.run<IOException> {
      file.rename(this, "renamed.excluded")
    }

    observedChange.awaitAfterCallback()
    assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isIndexable(file))
    val afterDelete = observedChange.afterEvents.get().filterIsInstance<VFileDeleteEvent>().single()
    assertSame(file, afterDelete.file)
    assertTrue(observedChange.afterEvents.get().none { it is VFilePropertyChangeEvent && it.isRename })
  }

  @Test
  fun `refresh reports materialized descendants after the VFS change`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot()
    val afterEvents = AtomicReference<List<VFileEvent>>()
    val afterCallback = CountDownLatch(1)

    ProjectBulkFileListenerRegistry.getInstance(projectModel.project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        afterEvents.set(events)
        afterCallback.countDown()
      }
    }, disposable)

    val createdTree = root.toNioPath().resolve("created-tree")
    Files.createDirectories(createdTree.resolve("src"))
    Files.writeString(createdTree.resolve("a.txt"), "a")
    Files.writeString(createdTree.resolve("src/B.kt"), "b")
    root.refresh(false, true)

    assertTrue(afterCallback.await(10, TimeUnit.SECONDS))
    val eventPaths = afterEvents.get().filterIsInstance<VFileCreateEvent>().mapNotNull { it.file?.path }.toSet()
    assertTrue(eventPaths.any { it.endsWith("/created-tree") })
    assertTrue(eventPaths.any { it.endsWith("/created-tree/a.txt") })
    assertTrue(eventPaths.any { it.endsWith("/created-tree/src") })
    assertTrue(eventPaths.any { it.endsWith("/created-tree/src/B.kt") })
  }

  private fun observeNextChange(disposable: Disposable): ObservedChange {
    val observedChange = ObservedChange()
    ProjectBulkFileListenerRegistry.getInstance(projectModel.project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        observedChange.afterEvents.set(events)
        observedChange.afterCallback.countDown()
      }
    }, disposable)
    return observedChange
  }

  private class ObservedChange {
    val afterEvents = AtomicReference<List<VFileEvent>>()
    val afterCallback = CountDownLatch(1)

    fun awaitAfterCallback() {
      assertTrue(afterCallback.await(10, TimeUnit.SECONDS))
    }
  }

  private fun addContentRoot(excludePattern: String? = null): VirtualFile {
    val module: Module = projectModel.createModule()
    val root = projectModel.baseProjectDir.newVirtualDirectory("content")
    ModuleRootModificationUtil.addContentRoot(module, root)
    if (excludePattern != null) {
      ModuleRootModificationUtil.updateModel(module) { model ->
        model.contentEntries.single().addExcludePattern(excludePattern)
      }
    }
    return root
  }
}
