// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
object FileLoadingTracker {
  private val LOG = logger<FileLoadingTracker>()

  private val trackedPaths = AtomicReference(initialState())

  private fun initialState(): TrackedPaths {
    val pathsToTrack = System.getProperty("file.system.trace.loading", "").split(";").filter { it.isNotBlank() }
    if (pathsToTrack.isEmpty()) {
      return TrackedPaths.EMPTY
    }
    return TrackedPaths.EMPTY.with(CollectionFactory.createFilePathSet(pathsToTrack, SystemInfoRt.isFileSystemCaseSensitive))
  }

  @JvmStatic
  fun fileLoaded(parent: VirtualDirectoryImpl, nameId: Int) {
    val tracked = trackedPaths.get()
    if (tracked.leafNameIds.contains(nameId)) {
      val path = parent.path + '/' + FSRecords.getInstance().getNameByNameId(nameId)
      if (tracked.paths.contains(path)) {
        LOG.info("Loading $path", Throwable())
      }
    }
  }

  fun startTracking(paths: Collection<String>): AccessToken {
    if (paths.isEmpty()) {
      return AccessToken.EMPTY_ACCESS_TOKEN
    }
    val registration = CollectionFactory.createFilePathSet(paths, SystemInfoRt.isFileSystemCaseSensitive)
    trackedPaths.updateAndGet { it.with(registration) }
    LOG.info("Started tracking VFS loading of $registration")
    return AccessToken.create {
      trackedPaths.updateAndGet { it.without(registration) }
      LOG.info("Stopped tracking VFS loading of $registration")
    }
  }

  private class TrackedPaths private constructor(private val registrations: List<Set<String>>) {
    val paths: Set<String>
    val leafNameIds: IntSet

    init {
      if (registrations.isEmpty()) {
        paths = setOf()
        leafNameIds = IntSets.EMPTY_SET
      }
      else {
        val union = CollectionFactory.createFilePathSet(registrations.sumOf { it.size }, SystemInfoRt.isFileSystemCaseSensitive)
        for (registration in registrations) {
          union.addAll(registration)
        }
        val nameIds = IntOpenHashSet(union.size)
        val fsRecords = FSRecords.getInstance()
        for (path in union) {
          nameIds.add(fsRecords.getNameId(StringUtil.getShortName(path, '/')))
        }
        paths = union
        leafNameIds = nameIds
      }
    }

    fun with(registration: Set<String>): TrackedPaths = TrackedPaths(registrations + listOf(registration))

    fun without(registration: Set<String>): TrackedPaths = TrackedPaths(registrations.filter { it !== registration })

    companion object {
      val EMPTY: TrackedPaths = TrackedPaths(emptyList())
    }
  }
}
