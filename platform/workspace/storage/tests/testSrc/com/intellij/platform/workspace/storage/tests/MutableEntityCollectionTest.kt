// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableEntityCollectionTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `check vfu list basic operations`() {
    val fileUrlList = listOf("/user/a.txt", "/user/opt/app/a.txt", "/user/opt/app/b.txt")
    val builder = createEmptyBuilder()
    builder.addListVFUEntity("hello", fileUrlList, virtualFileManager)

    makeOperationOnListAndCheck(builder, "/user/b.txt") { entity, vfu ->
      entity.fileProperty.add(vfu.single())
    }

    makeOperationOnListAndCheck(builder, "/user/c.txt") { entity, vfu ->
      entity.fileProperty.add(3, vfu.single())
    }

    makeOperationOnListAndCheck(builder, "/user/d.txt", "/user/e.txt") { entity, vfu ->
      entity.fileProperty.addAll(vfu)
    }

    makeOperationOnListAndCheck(builder, "/user/f.txt", "/user/g.txt") { entity, vfu ->
      entity.fileProperty.addAll(2, vfu)
    }

    makeOperationOnListAndCheck(builder, "/user/b.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.remove(vfu.single())
    }

    makeOperationOnListAndCheck(builder, "/user/d.txt", "/user/e.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeAll(vfu)
    }

    val entity = builder.entities(ListVFUEntity::class.java).single()
    makeOperationOnListAndCheck(builder, entity.fileProperty[3].url, removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeAt(3)
    }

    makeOperationOnListAndCheck(builder, "/user/foo.txt") { entity, vfu ->
      entity.fileProperty[3] = vfu.single()
    }

    makeOperationOnListAndCheck(builder, "/user/foo.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeIf { it == vfu.single() }
    }

    makeOperationOnListAndCheck(builder, "/user/opt/app/a.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeAll { vfu.contains(it) }
    }

    makeReplaceOnListOperationAndCheck(builder, listOf("/user/a.txt", "/user/f.txt"), listOf("/user/c.txt")) { entity, vfu ->
      entity.fileProperty.retainAll(listOf(virtualFileManager.getOrCreateFromUri("/user/c.txt")))
    }

    makeReplaceOnListOperationAndCheck(builder, listOf("/user/c.txt"), listOf("/user/e.txt")) { entity, vfu ->
      entity.fileProperty.replaceAll {
        if (it == vfu.single()) virtualFileManager.getOrCreateFromUri("/user/e.txt") else it
      }
    }

    makeOperationOnListAndCheck(builder, "/user/d.txt", "/user/f.txt", "/user/g.txt") { entity, vfu ->
      val listIterator = entity.fileProperty.listIterator()
      vfu.forEach { listIterator.add(it) }
    }

    makeReplaceOnListOperationAndCheck(builder, listOf("/user/d.txt"), listOf("/user/k.txt")) { entity, vfu ->
      val listIterator = entity.fileProperty.listIterator()
      while (listIterator.hasNext()) {
        val element = listIterator.next()
        if (element == vfu.single()) listIterator.set(virtualFileManager.getOrCreateFromUri("/user/k.txt"))
      }
    }

    makeOperationOnListAndCheck(builder, "/user/k.txt", removeOperation = true) { entity, vfu ->
      val listIterator = entity.fileProperty.listIterator()
      while (listIterator.hasNext()) {
        val element = listIterator.next()
        if (element == vfu.single()) listIterator.remove()
      }
    }

    makeOperationOnListAndCheck(builder, "/user/g.txt", removeOperation = true) { entity, vfu ->
      val listIterator = entity.fileProperty.iterator()
      while (listIterator.hasNext()) {
        val element = listIterator.next()
        if (element == vfu.single()) listIterator.remove()
      }
    }

    makeOperationOnListAndCheck(builder, "/user/e.txt", "/user/f.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.clear()
    }
  }

  @Test
  fun `check vfu set basic operations`() {
    val vfuSet = listOf("/user/a.txt", "/user/b.txt", "/user/c.txt", "/user/opt/app/a.txt").map { virtualFileManager.getOrCreateFromUri(it) }.toSet()
    val builder = createEmptyBuilder()
    builder.addEntity(SetVFUEntity("hello", vfuSet, SampleEntitySource("test")))

    makeOperationOnSetAndCheck(builder, "/user/d.txt") { entity, vfu ->
      entity.fileProperty.add(vfu.single())
    }

    makeOperationOnSetAndCheck(builder, "/user/f.txt", "/user/e.txt") { entity, vfu ->
      entity.fileProperty.addAll(vfu)
    }

    makeOperationOnSetAndCheck(builder, "/user/d.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.remove(vfu.single())
    }

    makeOperationOnSetAndCheck(builder, "/user/f.txt", "/user/e.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeAll(vfu)
    }

    makeOperationOnSetAndCheck(builder, "/user/a.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.removeIf { it == vfu.single() }
    }

    // TODO:: Not supported
    //makeOperationOnSetAndCheck(builder, "/user/opt/app/a.txt", removeOperation = true) { entity, vfu ->
    //  entity.fileProperty.removeAll { vfu.contains(it) }
    //}

    makeReplaceOnSetOperationAndCheck(builder, listOf("/user/b.txt", "/user/opt/app/a.txt"), listOf("/user/c.txt")) { entity, vfu ->
      entity.fileProperty.retainAll(listOf(virtualFileManager.getOrCreateFromUri("/user/c.txt")))
    }

    // TODO:: Not supported
    //makeOperationOnSetAndCheck(builder, "/user/c.txt", removeOperation = true) { entity, vfu ->
    //  val listIterator = entity.fileProperty.iterator()
    //  while (listIterator.hasNext()) {
    //    val element = listIterator.next()
    //    if (element == vfu.single()) listIterator.remove()
    //  }
    //}

    makeOperationOnSetAndCheck(builder, "/user/f.txt", "/user/e.txt") { entity, vfu ->
      entity.fileProperty.addAll(vfu)
    }

    makeOperationOnSetAndCheck(builder, "/user/e.txt", "/user/f.txt", "/user/c.txt", removeOperation = true) { entity, vfu ->
      entity.fileProperty.clear()
    }
  }

  private fun makeOperationOnSetAndCheck(builder: MutableEntityStorageImpl, vararg urls: String, removeOperation: Boolean = false,
                                         operation: (SetVFUEntity.Builder, Set<VirtualFileUrl>) -> Unit) {
    val entity = builder.entities(SetVFUEntity::class.java).single()
    val vfuForAction = urls.map { virtualFileManager.getOrCreateFromUri(it) }.toSet()

    var virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    if (removeOperation) vfuForAction.forEach { assertTrue(virtualFiles.contains(it)) }

    builder.modifyEntity(entity) {
      operation(this, vfuForAction)
    }

    virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach {
      if (removeOperation) {
        assertFalse(virtualFiles.contains(it))
      }
      else {
        assertTrue(virtualFiles.contains(it))
      }
    }
  }

  @Test
  fun `collection modification allowed only in modifyEntity block`() {
    val vfuSet = listOf("/user/a.txt", "/user/b.txt", "/user/c.txt", "/user/opt/app/a.txt").map { virtualFileManager.getOrCreateFromUri(it) }.toSet()
    val builder = createEmptyBuilder()
    builder.addEntity(SetVFUEntity("hello", vfuSet, SampleEntitySource("test")))
    val entity = builder.entities(SetVFUEntity::class.java).first()
    entity as SetVFUEntityImpl.Builder
    assertThrows<IllegalStateException> {
      entity.fileProperty.remove(entity.fileProperty.first())
    }
  }

  @Test
  fun `check lambda is available only in certain places`() {
    val vfuSet = listOf("/user/a.txt", "/user/b.txt", "/user/c.txt", "/user/opt/app/a.txt").map { virtualFileManager.getOrCreateFromUri(it) }.toSet()
    val builder = createEmptyBuilder()
    val entity = SetVFUEntity("hello", vfuSet, SampleEntitySource("test"))
    assertNotNull((entity.fileProperty as MutableWorkspaceSet).getModificationUpdateAction())
    (entity.fileProperty as MutableWorkspaceSet).remove(entity.fileProperty.first())
    builder.addEntity(entity)
    var existingEntity = builder.entities(SetVFUEntity::class.java).first()
    assertNull((existingEntity.fileProperty as MutableWorkspaceSet).getModificationUpdateAction())
    builder.modifyEntity(existingEntity) {
      assertNotNull((this.fileProperty as MutableWorkspaceSet).getModificationUpdateAction())
    }
    assertNull((existingEntity.fileProperty as MutableWorkspaceSet).getModificationUpdateAction())
    existingEntity = builder.entities(SetVFUEntity::class.java).first()
    assertNull((existingEntity.fileProperty as MutableWorkspaceSet).getModificationUpdateAction())
  }

  private fun makeReplaceOnSetOperationAndCheck(builder: MutableEntityStorageImpl, oldUrls: List<String>, newUrls: List<String>,
                                                operation: (SetVFUEntity.Builder, Set<VirtualFileUrl>) -> Unit) {
    val entity = builder.entities(SetVFUEntity::class.java).single()
    val vfuForAction = oldUrls.map { virtualFileManager.getOrCreateFromUri(it) }.toSet()

    var virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach { assertTrue(virtualFiles.contains(it)) }

    builder.modifyEntity(entity) {
      operation(this, vfuForAction)
    }

    virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach { assertFalse(virtualFiles.contains(it)) }
    newUrls.map { virtualFileManager.getOrCreateFromUri(it) }.forEach { assertTrue(virtualFiles.contains(it)) }
  }

  private fun makeOperationOnListAndCheck(builder: MutableEntityStorageImpl, vararg urls: String, removeOperation: Boolean = false,
                                          operation: (ListVFUEntity.Builder, List<VirtualFileUrl>) -> Unit) {
    val entity = builder.entities(ListVFUEntity::class.java).single()
    val vfuForAction = urls.map { virtualFileManager.getOrCreateFromUri(it) }

    var virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    if (removeOperation) vfuForAction.forEach { assertTrue(virtualFiles.contains(it)) }

    builder.modifyEntity(entity) {
      operation(this, vfuForAction)
    }

    virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach {
      if (removeOperation) {
        assertFalse(virtualFiles.contains(it))
      }
      else {
        assertTrue(virtualFiles.contains(it))
      }
    }
  }

  private fun makeReplaceOnListOperationAndCheck(builder: MutableEntityStorageImpl, oldUrls: List<String>, newUrls: List<String>,
                                                 operation: (ListVFUEntity.Builder, List<VirtualFileUrl>) -> Unit) {
    val entity = builder.entities(ListVFUEntity::class.java).single()
    val vfuForAction = oldUrls.map { virtualFileManager.getOrCreateFromUri(it) }

    var virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach { assertTrue(virtualFiles.contains(it)) }

    builder.modifyEntity(entity) {
      operation(this, vfuForAction)
    }

    virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id)
    vfuForAction.forEach { assertFalse(virtualFiles.contains(it)) }
    newUrls.map { virtualFileManager.getOrCreateFromUri(it) }.forEach { assertTrue(virtualFiles.contains(it)) }
  }
}