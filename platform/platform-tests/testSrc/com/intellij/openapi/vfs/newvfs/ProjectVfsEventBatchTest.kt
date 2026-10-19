// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.mock.MockFileTypeManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.ChildInfo
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files

@TestApplication
internal class ProjectVfsEventBatchTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `create filters before materialization and uses real parents after`() {
    val root = contentRoot()
    val children = arrayOf(info("kept.txt"), info("omitted.excluded"), info("missing", arrayOf(info("child.txt"))))
    val event = VFileCreateEvent(this, root, "created", true, null, null, children)
    val batch = prepare(event)
    lateinit var created: VirtualFile
    lateinit var kept: VirtualFile
    runWriteActionAndWait {
      created = root.createChildDirectory(this, "created")
      kept = created.createChildData(this, "kept.txt")
      created.createChildData(this, "omitted.excluded")
    }

    val events = after(batch)
    assertEquals(2, events.size)
    assertSame(event, events.first())
    val childEvent = events.last() as VFileCreateEvent
    assertSame(created, childEvent.parent)
    assertEquals(kept, childEvent.file)
    assertSame(children[0].children, childEvent.children)
  }

  @Test
  fun `copy preserves metadata and skips unresolved descendants`() {
    val root = contentRoot()
    val source = projectModel.baseProjectDir.newVirtualDirectory("source")
    val sourceChild = projectModel.baseProjectDir.newVirtualFile("source/kept.txt")
    val children = arrayOf(info("kept.txt"), info("missing-source", arrayOf(info("child.txt"))), info("omitted.excluded"))
    val event = VFileCopyEvent(this, source, root, "copied", null, null, children, true)
    val batch = prepare(event)
    lateinit var copied: VirtualFile
    runWriteActionAndWait {
      copied = root.createChildDirectory(this, "copied")
      copied.createChildData(this, "kept.txt")
      copied.createChildDirectory(this, "missing-source").createChildData(this, "child.txt")
      copied.createChildData(this, "omitted.excluded")
    }

    val events = after(batch)
    assertEquals(2, events.size)
    assertSame(event, events.first())
    val childEvent = events.last() as VFileCopyEvent
    assertEquals(sourceChild, childEvent.file)
    assertSame(copied, childEvent.newParent)
    assertSame(children[0].fileAttributes, childEvent.attributes)
    assertSame(children[0].children, childEvent.children)
    assertEquals(children[0].isAllChildren, childEvent.isAllChildren)
  }

  @Test
  fun `move in filters cached descendants at the destination`() {
    val root = contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("outside/tree")
    val kept = projectModel.baseProjectDir.newVirtualFile("outside/tree/kept.txt")
    projectModel.baseProjectDir.newVirtualFile("outside/tree/omitted.excluded")
    val event = VFileMoveEvent(this, directory, root)
    val batch = prepare(event)
    runWriteActionAndWait { directory.move(this, root) }

    val events = after(batch).map { it as VFileCreateEvent }
    assertEquals(setOf(directory, kept), events.map { it.file }.toSet())
    assertTrue(events.all { it.parent == it.file?.parent })
  }

  @Test
  fun `move within the project includes create events for cached descendants`() {
    val root = contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val nested = projectModel.baseProjectDir.newVirtualDirectory("content/tree/nested")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/nested/child.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree/omitted.excluded")
    val destination = projectModel.baseProjectDir.newVirtualDirectory("content/destination")
    val event = VFileMoveEvent(this, directory, destination)
    val batch = prepare(event)
    runWriteActionAndWait { directory.move(this, destination) }

    val events = after(batch)
    assertEquals(3, events.size)
    assertSame(event, events.first())
    assertSame(root, event.oldParent)
    assertSame(destination, event.newParent)
    assertSame(this, event.requestor)
    val childEvents = events.drop(1).map { it as VFileCreateEvent }
    assertEquals(setOf(nested, child), childEvents.map { it.file }.toSet())
    for (childEvent in childEvents) {
      assertSame(childEvent.file?.parent, childEvent.parent)
      assertEquals(childEvent.file?.path, childEvent.path)
      assertSame(this, childEvent.requestor)
    }
  }

  @Test
  fun `move between content roots creates events for newly included descendants`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val nested = projectModel.baseProjectDir.newVirtualDirectory("content/tree/nested.excluded")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/nested.excluded/child.txt")
    val destination = projectModel.baseProjectDir.newVirtualDirectory("destination")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("destination"), destination)
    runReadActionBlocking {
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
      assertTrue(index.isInWorkspace(directory))
      assertFalse(index.isInWorkspace(nested))
      assertFalse(index.isInWorkspace(child))
    }
    val event = VFileMoveEvent(this, directory, destination)
    val batch = prepare(event)
    runWriteActionAndWait { directory.move(this, destination) }

    val events = after(batch)
    assertSame(event, events.first())
    val childEvents = events.drop(1).map { it as VFileCreateEvent }
    assertEquals(setOf(nested, child), childEvents.map { it.file }.toSet())
    assertTrue(childEvents.all { it.parent == it.file?.parent })
  }

  @Test
  fun `rename in filters the cached tree with the new name`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree.excluded")
    val kept = projectModel.baseProjectDir.newVirtualFile("content/tree.excluded/kept.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree.excluded/omitted.excluded")
    val event = VFilePropertyChangeEvent(this, directory, VirtualFile.PROP_NAME, directory.name, "tree")
    val batch = prepare(event)
    runWriteActionAndWait { directory.rename(this, "tree") }

    val events = after(batch).map { it as VFileCreateEvent }
    assertEquals(setOf(directory, kept), events.map { it.file }.toSet())
    assertTrue(events.all { it.parent == it.file?.parent })
  }

  @Test
  fun `move out filters cached descendants in deletes`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/child.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree/omitted.excluded")
    projectModel.baseProjectDir.newVirtualFile("content/tree/directory.excluded/omitted.txt")
    val outside = projectModel.baseProjectDir.newVirtualDirectory("outside")
    val batch = prepare(VFileMoveEvent(this, directory, outside))
    runWriteActionAndWait { directory.move(this, outside) }

    val events = after(batch).map { it as VFileDeleteEvent }
    assertEquals(setOf(directory, child), events.map { it.file }.toSet())
  }

  @Test
  fun `rename out filters cached descendants in deletes`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/child.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree/omitted.excluded")
    projectModel.baseProjectDir.newVirtualFile("content/tree/directory.excluded/omitted.txt")
    val batch = prepare(VFilePropertyChangeEvent(this, directory, VirtualFile.PROP_NAME, directory.name, "tree.excluded"))
    runWriteActionAndWait { directory.rename(this, "tree.excluded") }

    val events = after(batch).map { it as VFileDeleteEvent }
    assertEquals(setOf(directory, child), events.map { it.file }.toSet())
  }

  @ParameterizedTest
  @CsvSource("false, false", "false, true", "true, false", "true, true")
  fun `move or rename reports nested content below an outside parent`(rename: Boolean, excludedParent: Boolean) {
    contentRoot()
    val directoryPath = if (excludedParent) "content/tree.excluded" else "outside/tree"
    val directory = projectModel.baseProjectDir.newVirtualDirectory(directoryPath)
    projectModel.baseProjectDir.newVirtualFile("$directoryPath/omitted.txt")
    val skippedFiles = (0 until 100).map {
      projectModel.baseProjectDir.newVirtualFile("$directoryPath/unrelated/child-$it.txt")
    }.toSet()
    val nested = projectModel.baseProjectDir.newVirtualDirectory("$directoryPath/nested")
    val child = projectModel.baseProjectDir.newVirtualFile("$directoryPath/nested/child.txt")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("nested"), nested)
    val destination = projectModel.baseProjectDir.newVirtualDirectory("destination")
    val newName = if (excludedParent) "renamed.excluded" else "renamed"
    val event = if (rename) {
      VFilePropertyChangeEvent(this, directory, VirtualFile.PROP_NAME, directory.name, newName)
    }
    else {
      VFileMoveEvent(this, directory, destination)
    }
    runReadActionBlocking {
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
      assertFalse(index.isInWorkspace(directory))
      assertTrue(index.isInWorkspace(nested))
    }
    val batch = prepareWithoutVisiting(skippedFiles, event)
    runWriteActionAndWait {
      if (rename) directory.rename(this, newName) else directory.move(this, destination)
    }

    val events = after(batch).map { it as VFileDeleteEvent }
    assertEquals(setOf(nested, child), events.map { it.file }.toSet())
    assertEquals(2, events.size)
    assertTrue(events.all { it.requestor === this })
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `move or rename includes nested content at an outside destination`(rename: Boolean) {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("outside/tree")
    projectModel.baseProjectDir.newVirtualFile("outside/tree/omitted.txt")
    val skippedFiles = (0 until 100).map {
      projectModel.baseProjectDir.newVirtualFile("outside/tree/unrelated/child-$it.txt")
    }.toSet()
    val nested = projectModel.baseProjectDir.newVirtualDirectory("outside/tree/nested")
    val child = projectModel.baseProjectDir.newVirtualFile("outside/tree/nested/child.txt")
    val destination = projectModel.baseProjectDir.newVirtualDirectory("destination")
    val futureUrl = if (rename) directory.parent.url + "/renamed" else destination.url + "/tree"
    ModuleRootModificationUtil.updateModel(projectModel.createModule("nested")) { model ->
      model.addContentEntry("$futureUrl/nested")
    }
    val event = if (rename) {
      VFilePropertyChangeEvent(this, directory, VirtualFile.PROP_NAME, directory.name, "renamed")
    }
    else {
      VFileMoveEvent(this, directory, destination)
    }
    runReadActionBlocking {
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
      assertFalse(index.isInWorkspace(directory))
      assertFalse(index.isInWorkspace(nested))
    }
    val batch = prepareWithoutVisiting(skippedFiles, event)
    runWriteActionAndWait {
      if (rename) directory.rename(this, "renamed") else directory.move(this, destination)
    }

    val events = after(batch).map { it as VFileCreateEvent }
    assertEquals(setOf(nested, child), events.map { it.file }.toSet())
    assertEquals(2, events.size)
    assertTrue(events.all { it.parent == it.file?.parent && it.requestor === this })
  }

  @Test
  fun `delete keeps the original event and filters cached descendants`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/child.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree/omitted.excluded")
    projectModel.baseProjectDir.newVirtualFile("content/tree/directory.excluded/omitted.txt")
    val nested = projectModel.baseProjectDir.newVirtualDirectory("content/tree/directory.excluded/nested")
    val nestedChild = projectModel.baseProjectDir.newVirtualFile("content/tree/directory.excluded/nested/child.txt")
    val nestedModule = projectModel.createModule("nested")
    ModuleRootModificationUtil.addContentRoot(nestedModule, nested)
    val event = VFileDeleteEvent(this, directory)
    val batch = prepare(event)
    runWriteActionAndWait { directory.delete(this) }

    val events = after(batch)
    assertSame(event, events.first())
    assertEquals(setOf(directory, child, nested, nestedChild), events.map { (it as VFileDeleteEvent).file }.toSet())
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `delete reports nested content below a parent outside the workspace`(excludedParent: Boolean) {
    contentRoot()
    val directoryPath = if (excludedParent) "content/tree.excluded" else "outside/tree"
    val directory = projectModel.baseProjectDir.newVirtualDirectory(directoryPath)
    projectModel.baseProjectDir.newVirtualFile("$directoryPath/omitted.txt")
    val skippedFiles = (0 until 100).map {
      projectModel.baseProjectDir.newVirtualFile("$directoryPath/unrelated/child-$it.txt")
    }.toSet()
    val nested = projectModel.baseProjectDir.newVirtualDirectory("$directoryPath/nested")
    val child = projectModel.baseProjectDir.newVirtualFile("$directoryPath/nested/child.txt")
    val nestedModule = projectModel.createModule("nested")
    ModuleRootModificationUtil.addContentRoot(nestedModule, nested)
    runReadActionBlocking {
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
      assertFalse(index.isInWorkspace(directory))
      assertTrue(index.isInWorkspace(nested))
    }
    val batch = prepareWithoutVisiting(skippedFiles, VFileDeleteEvent(this, directory))
    runWriteActionAndWait { directory.delete(this) }

    assertEquals(setOf(nested, child), after(batch).map { (it as VFileDeleteEvent).file }.toSet())
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `delete skips cached children when no workspace roots exist below`(excludedParent: Boolean) {
    contentRoot()
    val directoryPath = if (excludedParent) "content/tree.excluded" else "outside/tree"
    val directory = projectModel.baseProjectDir.newVirtualDirectory(directoryPath)
    val skippedFiles = (0 until 100).map {
      projectModel.baseProjectDir.newVirtualFile("$directoryPath/child-$it.txt")
    }.toSet()
    projectModel.project.workspaceModel.getVirtualFileUrlManager().storeAndGet(directory.url)

    val batch = prepareWithoutVisiting(skippedFiles, VFileDeleteEvent(this, directory))

    assertTrue(after(batch).isEmpty())
  }

  @Test
  fun `nested content below an excluded new directory stays included`() {
    val root = contentRoot()
    val nestedModule = projectModel.createModule("nested")
    ModuleRootModificationUtil.updateModel(nestedModule) { model ->
      model.addContentEntry(root.url + "/tree.excluded/nested")
    }
    val event = VFileCreateEvent(this, root, "tree.excluded", true, null, null,
                                 arrayOf(info("nested", arrayOf(info("child.txt")))))
    val batch = prepare(event)
    lateinit var nested: VirtualFile
    lateinit var child: VirtualFile
    runWriteActionAndWait {
      nested = root.createChildDirectory(this, "tree.excluded").createChildDirectory(this, "nested")
      child = nested.createChildData(this, "child.txt")
    }

    assertEquals(setOf(nested, child), after(batch).map { (it as VFileCreateEvent).file }.toSet())
  }

  @Test
  fun `content changes within the project keep the original event`() {
    contentRoot()
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/child.txt")
    val contentEvent = VFileContentChangeEvent(this, child, child.modificationStamp, child.modificationStamp + 1)
    val batch = prepare(contentEvent)

    assertSame(contentEvent, after(batch).single())
  }

  @Test
  fun `rename within the project includes create events for cached descendants`() {
    contentRoot()
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val nested = projectModel.baseProjectDir.newVirtualDirectory("content/tree/nested")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/nested/child.txt")
    projectModel.baseProjectDir.newVirtualFile("content/tree/omitted.excluded")
    val event = VFilePropertyChangeEvent(this, directory, VirtualFile.PROP_NAME, directory.name, "renamed")
    val batch = prepare(event)
    runWriteActionAndWait { directory.rename(this, "renamed") }

    val events = after(batch)
    assertSame(event, events.first())
    assertEquals(3, events.size)
    val childEvents = events.drop(1).map { it as VFileCreateEvent }
    assertEquals(setOf(nested, child), childEvents.map { it.file }.toSet())
    for (childEvent in childEvents) {
      assertSame(childEvent.file?.parent, childEvent.parent)
      assertEquals(childEvent.file?.path, childEvent.path)
      assertSame(this, childEvent.requestor)
    }
  }

  @Test
  fun `exclusion conditions run only during preparation`() {
    val root = contentRoot()
    var checkLocks = false
    var checks = 0
    val condition = object : WorkspaceFileSetExclusionCondition {
      override fun shouldExclude(file: VirtualFile): Boolean {
        if (checkLocks) {
          assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)
          checks++
        }
        return file.name.endsWith(".excluded")
      }

      override fun equals(other: Any?): Boolean = other === this

      override fun hashCode(): Int = System.identityHashCode(this)
    }
    runWriteActionAndWait {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(object : WorkspaceFileIndexContributor<ContentRootEntity> {
        override val entityClass: Class<ContentRootEntity> get() = ContentRootEntity::class.java

        override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
          registrar.registerExclusionCondition(entity.url, condition, entity)
        }
      }, projectModel.disposableRule.disposable)
      WorkspaceFileIndexEx.getInstance(projectModel.project).reset()
    }
    val event = VFileCreateEvent(this, root, "created.txt", false, null, null, null)
    checkLocks = true
    val batch = prepare(event)
    assertTrue(checks > 0)
    checkLocks = false
    runWriteActionAndWait { root.createChildData(this, "created.txt") }
    checkLocks = true
    runWriteActionAndWait {
      val checksBefore = checks
      assertSame(event, batch.getAfterEvents().single())
      assertEquals(checksBefore, checks)
    }
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `non-indexable creates include only cached files`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind) as NewVirtualFile
    val children = arrayOf(info("cached.txt"), info("uncached", arrayOf(info("child.txt"))))
    val event = VFileCreateEvent(this, root, "created", true, null, null, children)
    val uncachedEvent = VFileCreateEvent(this, root, "uncached", true, null, null, arrayOf(info("child.txt")))
    val batch = prepare(event, uncachedEvent)
    lateinit var created: NewVirtualFile
    lateinit var cached: VirtualFile
    runWriteActionAndWait {
      created = root.createChildDirectory(this, "created") as NewVirtualFile
      cached = created.createChildData(this, "cached.txt")
    }
    Files.createDirectories(created.toNioPath().resolve("uncached"))
    Files.writeString(created.toNioPath().resolve("uncached/child.txt"), "text")
    Files.createDirectories(root.toNioPath().resolve("uncached"))
    Files.writeString(root.toNioPath().resolve("uncached/child.txt"), "text")

    val events = after(batch)
    assertEquals(2, events.size)
    assertSame(event, events.first())
    assertEquals(cached, (events.last() as VFileCreateEvent).file)
    assertNull(created.findChildIfCached("uncached"))
    assertNull(root.findChildIfCached("uncached"))
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `non-indexable copies include only cached files`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    val source = projectModel.baseProjectDir.newVirtualDirectory("source")
    val sourceChild = projectModel.baseProjectDir.newVirtualFile("source/cached.txt")
    val children = arrayOf(info("cached.txt"), info("uncached.txt"))
    val event = VFileCopyEvent(this, source, root, "copied", null, null, children, true)
    val batch = prepare(event)
    val copied = runWriteActionAndWait {
      (root.createChildDirectory(this, "copied") as NewVirtualFile).also {
        it.createChildData(this, "cached.txt")
      }
    }
    Files.writeString(source.toNioPath().resolve("uncached.txt"), "text")
    Files.writeString(copied.toNioPath().resolve("uncached.txt"), "text")

    val events = after(batch)
    assertEquals(2, events.size)
    assertSame(event, events.first())
    val childEvent = events.last() as VFileCopyEvent
    assertEquals(sourceChild, childEvent.file)
    assertSame(copied, childEvent.newParent)
    assertNull(copied.findChildIfCached("uncached.txt"))
    assertNull((source as NewVirtualFile).findChildIfCached("uncached.txt"))
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `move into a non-indexable root includes cached descendants`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    val directory = projectModel.baseProjectDir.newVirtualDirectory("outside/tree")
    val child = projectModel.baseProjectDir.newVirtualFile("outside/tree/child.txt")
    val batch = prepare(VFileMoveEvent(this, directory, root))
    runWriteActionAndWait { directory.move(this, root) }

    assertEquals(setOf(directory, child), after(batch).map { (it as VFileCreateEvent).file }.toSet())
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `move from content to a non-indexable root includes cached descendants`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    val directory = projectModel.baseProjectDir.newVirtualDirectory("content/tree")
    val child = projectModel.baseProjectDir.newVirtualFile("content/tree/child.txt")
    val event = VFileMoveEvent(this, directory, root)
    val batch = prepare(event)
    runWriteActionAndWait { directory.move(this, root) }

    val events = after(batch)
    assertSame(event, events.first())
    val childEvents = events.drop(1).map { it as VFileCreateEvent }
    assertEquals(listOf(child), childEvents.map { it.file })
    assertSame(directory, childEvents.single().parent)
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `non-indexable deletes and content changes include cached files`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    lateinit var directory: VirtualFile
    lateinit var child: VirtualFile
    runWriteActionAndWait {
      directory = root.createChildDirectory(this, "tree")
      child = directory.createChildData(this, "child.txt")
    }
    val contentEvent = VFileContentChangeEvent(this, child, child.modificationStamp, child.modificationStamp + 1)
    val deleteEvent = VFileDeleteEvent(this, directory)
    val batch = prepare(contentEvent, deleteEvent)
    runWriteActionAndWait { directory.delete(this) }

    val events = after(batch)
    assertSame(contentEvent, events[0])
    assertSame(deleteEvent, events[1])
    assertEquals(setOf(directory, child), events.drop(1).map { (it as VFileDeleteEvent).file }.toSet())
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `rename within a non-indexable root keeps the original event`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    val file = runWriteActionAndWait { root.createChildData(this, "child.txt") }
    val event = VFilePropertyChangeEvent(this, file, VirtualFile.PROP_NAME, file.name, "renamed.txt")
    val batch = prepare(event)
    runWriteActionAndWait { file.rename(this, "renamed.txt") }

    assertTrue(file.isValid)
    assertSame(event, after(batch).single())
  }

  @ParameterizedTest
  @EnumSource(WorkspaceFileKind::class, names = ["CONTENT_NON_INDEXABLE", "EXTERNAL_NON_INDEXABLE"])
  fun `move from a non-indexable root to content keeps the original event`(kind: WorkspaceFileKind) {
    val root = nonIndexableRoot(kind)
    val file = runWriteActionAndWait { root.createChildData(this, "child.txt") }
    val destination = projectModel.baseProjectDir.newVirtualDirectory("content/destination")
    val event = VFileMoveEvent(this, file, destination)
    val batch = prepare(event)
    runWriteActionAndWait { file.move(this, destination) }

    assertSame(event, after(batch).single())
  }

  private fun nonIndexableRoot(kind: WorkspaceFileKind): VirtualFile {
    contentRoot()
    val root = projectModel.baseProjectDir.newVirtualDirectory("non-indexable")
    runWriteActionAndWait {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(object : WorkspaceFileIndexContributor<ContentRootEntity> {
        override val entityClass: Class<ContentRootEntity> get() = ContentRootEntity::class.java

        override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
          registrar.registerFileSet(entity.url.parent!!.append("non-indexable"), kind, entity, null)
        }
      }, projectModel.disposableRule.disposable)
      WorkspaceFileIndexEx.getInstance(projectModel.project).reset()
    }
    runReadActionBlocking {
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
      assertTrue(index.isInWorkspace(root))
      assertFalse(index.isIndexable(root))
    }
    return root
  }

  private fun contentRoot(): VirtualFile {
    val root = projectModel.baseProjectDir.newVirtualDirectory("content")
    val module = projectModel.createModule()
    ModuleRootModificationUtil.addContentRoot(module, root)
    ModuleRootModificationUtil.updateModel(module) { it.contentEntries.single().addExcludePattern("*.excluded") }
    return root
  }

  private fun info(name: String, children: Array<ChildInfo>? = null): ChildInfo =
    ChildInfoImpl(VirtualFileManager.getInstance().storeName(name), null, children, null, true)

  private fun prepare(vararg events: VFileEvent): ProjectVfsEventBatch =
    runReadActionBlocking { ProjectVfsEventBatch(projectModel.project, events.toList()) }

  private fun prepareWithoutVisiting(skippedFiles: Set<VirtualFile>, vararg events: VFileEvent): ProjectVfsEventBatch {
    val fileTypeRegistry = FileTypeRegistry.getInstance()
    val disposable = Disposer.newDisposable(projectModel.disposableRule.disposable)
    try {
      FileTypeRegistry.setInstanceSupplier({
        object : MockFileTypeManager(PlainTextFileType.INSTANCE) {
          override fun isFileIgnored(file: VirtualFile): Boolean {
            assertFalse(file in skippedFiles, "The traversal must skip $file")
            return fileTypeRegistry.isFileIgnored(file)
          }
        }
      }, disposable)
      return prepare(*events)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun after(batch: ProjectVfsEventBatch): List<VFileEvent> {
    lateinit var events: List<VFileEvent>
    runWriteActionAndWait { events = batch.getAfterEvents() }
    return events
  }
}
