// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Path

object VfsDiffGenerator {
  class FileFlags(val flags: Int) {
    enum class FileStatus(val bit: Int) {
      CHILDREN_CACHED(PersistentFS.Flags.CHILDREN_CACHED),
      IS_DIRECTORY(PersistentFS.Flags.IS_DIRECTORY),
      IS_READ_ONLY(PersistentFS.Flags.IS_READ_ONLY),
      MUST_RELOAD_CONTENT(PersistentFS.Flags.MUST_RELOAD_CONTENT),
      IS_SYMLINK(PersistentFS.Flags.IS_SYMLINK),
      IS_SPECIAL(PersistentFS.Flags.IS_SPECIAL),
      IS_HIDDEN(PersistentFS.Flags.IS_HIDDEN),
      MUST_RELOAD_LENGTH(PersistentFS.Flags.MUST_RELOAD_LENGTH),
      CHILDREN_CASE_SENSITIVE(PersistentFS.Flags.CHILDREN_CASE_SENSITIVE),
      CHILDREN_CASE_SENSITIVITY_CACHED(PersistentFS.Flags.CHILDREN_CASE_SENSITIVITY_CACHED),
      FREE_RECORD_FLAG(PersistentFS.Flags.FREE_RECORD_FLAG),
      OFFLINE_BY_DEFAULT(PersistentFS.Flags.OFFLINE_BY_DEFAULT)
    }

    val status: Set<FileStatus> = FileStatus.values().filter { (it.bit and flags) != 0 }.toSortedSet().let {
      if (it.contains(FileStatus.FREE_RECORD_FLAG)) setOf(FileStatus.FREE_RECORD_FLAG)
      else it
    }

    override fun toString(): String {
      return status.toList().toString()
    }

    fun diff(targetFlags: FileFlags): Pair<List<FileStatus>, List<FileStatus>> {
      val targetStatus = targetFlags.status
      return status.minus(targetStatus).toList() to targetStatus.minus(status).toList()
    }
  }

  sealed interface DiffElement {
    data class RootsDiff(val rootsRemoved: List<Int>, val rootsAdded: List<Int>): DiffElement
    data class FileStatusChanged(val fileId: Int, val removed: List<FileFlags.FileStatus>, val added: List<FileFlags.FileStatus>): DiffElement
    data class ChildrenDiff(val fileId: Int, val childrenRemoved: List<Int>, val childrenAdded: List<Int>): DiffElement
    data class PropertyDiff(val fileId: Int, val description: String): DiffElement
    data class ContentDiff(val fileId: Int, val before: String?, val after: String?): DiffElement {
      override fun toString(): String {
        return "ContentDiff(fileId=$fileId, before=\n$before\n\n, after=\n$after\n)"
      }
    }
  }

  data class DiffResult(val filesVisited: Int, val elements: List<DiffElement>) {
    override fun toString(): String {
      return "visited $filesVisited files\n" +
        if (elements.isEmpty()) {
          "no diff"
        } else {
          "diff:\n" +
          elements.joinToString("\n")
        }
    }
  }

  fun buildDiff(baseVfs: FSRecordsImpl, targetVfs: FSRecordsImpl, maxDiffElements: Int = 10_000): DiffResult {
    val diff = mutableListOf<DiffElement>()
    var idsVisited = 0

    val rootsBase = baseVfs.listRoots().toList()
    val rootsTarget = baseVfs.listRoots().toList()
    if (rootsBase != rootsTarget) {
      diff.add(DiffElement.RootsDiff(rootsBase - rootsTarget.toSet(), rootsTarget - rootsBase.toSet()))
    }

    val queue = ArrayDeque<Int>()
    queue.addAll(rootsBase.toSet().intersect(rootsTarget.toSet()))

    while (queue.isNotEmpty() && diff.size < 10_000) {
      val id = queue.removeFirst()
      idsVisited++

      val baseFlags = baseVfs.getFlags(id).let(::FileFlags)
      val targetFlags = targetVfs.getFlags(id).let(::FileFlags)

      if (baseFlags.status != targetFlags.status) {
        val (removed, added) = baseFlags.diff(targetFlags)
        diff.add(DiffElement.FileStatusChanged(id, removed, added))
      }

      val baseChildren = baseVfs.listIds(id).toList().sorted()
      val targetChildren = targetVfs.listIds(id).toList().sorted()

      if (baseChildren != targetChildren) {
        val removed = baseChildren - targetChildren.toSet()
        val added = targetChildren - baseChildren.toSet()
        for (childId in removed) {
          check(!baseVfs.isDeleted(childId))
          check(baseVfs.getParent(childId) == id)
        }
        diff.add(DiffElement.ChildrenDiff(id, removed, added))
      }

      val baseContent = baseVfs.readContent(id)?.readAllBytes()
      val targetContent = targetVfs.readContent(id)?.readAllBytes()
      if (!baseContent.contentEquals(targetContent)) {
        diff.add(DiffElement.ContentDiff(id, baseContent?.decodeToString(), targetContent?.decodeToString()))
      }

      val baseNameId = baseVfs.getNameIdByFileId(id)
      val targetNameId = targetVfs.getNameIdByFileId(id)
      if (baseNameId != targetNameId) {
        diff.add(DiffElement.PropertyDiff(id, "name id $baseNameId -> $targetNameId"))
      }

      val baseParentId = baseVfs.getParent(id)
      val targetParentId = targetVfs.getParent(id)
      if (baseParentId != targetParentId) {
        diff.add(DiffElement.PropertyDiff(id, "parent id $baseParentId -> $targetParentId"))
      }

      val baseTimestamp = baseVfs.getTimestamp(id)
      val targetTimestamp = targetVfs.getTimestamp(id)
      if (baseTimestamp != targetTimestamp) {
        diff.add(DiffElement.PropertyDiff(id, "timestamp $baseTimestamp -> $targetTimestamp"))
      }

      val baseLength = baseVfs.getLength(id)
      val targetLength = targetVfs.getLength(id)
      if (baseLength != targetLength) {
        diff.add(DiffElement.PropertyDiff(id, "length $baseLength -> $targetLength"))
      }

      // TODO attributes?

      queue.addAll(baseChildren.toSet().intersect(targetChildren.toSet()))
    }
    return DiffResult(idsVisited, diff)
  }

  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <GenerateVFSDiff> <path to base caches folder> <path to target caches folder>" }
    val baseCachesDir = Path.of(args[0])
    val targetCachesDir = Path.of(args[1])

    val baseVfs = FSRecordsImpl.connect(baseCachesDir)
    val targetVfs = FSRecordsImpl.connect(targetCachesDir)
    val diff: DiffResult

    AutoCloseable {
      baseVfs.dispose()
      targetVfs.dispose()
      AppExecutorUtil.shutdownApplicationScheduledExecutorService()
    }.use {
      diff = buildDiff(baseVfs, targetVfs)
    }

    println(diff)
  }
}