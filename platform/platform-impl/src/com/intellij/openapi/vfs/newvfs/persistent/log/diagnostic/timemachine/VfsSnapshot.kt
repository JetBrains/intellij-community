// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface VfsSnapshot {
  val point: () -> OperationLogStorage.Iterator

  fun getFileById(fileId: Int): VirtualFileSnapshot
  fun getFileByIdIfExists(fileId: Int): VirtualFileSnapshot?

  interface VirtualFileSnapshot {
    val nameId: Lazy<Int?>
    val name: Lazy<String?>
    val parentId: Lazy<Int?>
    val parent: Lazy<VirtualFileSnapshot?>
  }
}

open class VfsSnapshotBase(point: OperationLogStorage.Iterator, private val id2name: (Int) -> String?) : VfsSnapshot {
  override val point = point.constCopier()

  protected val fileCache: ConcurrentMap<Int, VfsSnapshot.VirtualFileSnapshot> = ConcurrentHashMap()

  override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot = fileCache.computeIfAbsent(fileId) { VirtualFileSnapshotBase(fileId) }
  override fun getFileByIdIfExists(fileId: Int): VfsSnapshot.VirtualFileSnapshot? = fileCache.get(fileId)

  open inner class VirtualFileSnapshotBase(val fileId: Int) : VfsSnapshot.VirtualFileSnapshot {
    override val nameId: Lazy<Int?> = lazy { VfsChronicle.figureOutNameId(point(), fileId) }
    override val name: Lazy<String?> = lazy { nameId.value?.let(id2name) }

    override val parentId: Lazy<Int?> = lazy { VfsChronicle.figureOutParentId(point(), fileId) }
    override val parent: Lazy<VfsSnapshot.VirtualFileSnapshot?> = lazy { parentId.value?.let(::getFileById) }
  }
}

class VfsSnapshotWithInheritance(
  point: OperationLogStorage.Iterator,
  id2name: (Int) -> String?,
  precedingSnapshot: VfsSnapshot
) : VfsSnapshotBase(point, id2name) {
  val precedingSnapshot = SoftReference(precedingSnapshot)
  val precedingPoint = precedingSnapshot.point().constCopier()

  override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot =
    fileCache.computeIfAbsent(fileId) { VirtualFileSnapshotWithInheritance(fileId) }

  inner class VirtualFileSnapshotWithInheritance(fileId: Int) : VirtualFileSnapshotBase(fileId) {
    override val nameId: Lazy<Int?> = lazy {
      val splitIter = precedingPoint()
      VfsChronicle.figureOutNameId(point(), fileId) { it != splitIter }?.let { return@lazy it }
      precedingSnapshot.get()?.getFileByIdIfExists(fileId)?.let { println("DELEGATE nameId"); return@lazy it.nameId.value }
      VfsChronicle.figureOutNameId(splitIter, fileId)
    }
    override val parentId: Lazy<Int?> = lazy {
      val splitIter = precedingPoint()
      VfsChronicle.figureOutParentId(point(), fileId) { it != splitIter }?.let { return@lazy it }
      precedingSnapshot.get()?.getFileByIdIfExists(fileId)?.let { println("DELEGATE parentId"); return@lazy it.parentId.value }
      VfsChronicle.figureOutParentId(splitIter, fileId)
    }
  }
}

fun VfsSnapshot.VirtualFileSnapshot.fullPath(): String? =
  if (parentId.value != null) {
    parent.value?.fullPath()?.let { parPath ->
      name.value?.let { name ->
        return "$parPath/$name"
      }
    }
  }
  else {
    name.value ?: ""
  }