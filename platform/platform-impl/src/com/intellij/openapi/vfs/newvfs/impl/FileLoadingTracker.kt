// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets

internal object FileLoadingTracker {
  private val LOG = logger<FileLoadingTracker>()
  private val ourPaths: Set<String>
  private val ourLeafNameIds: IntSet

  init {
    val pathsToTrack = System.getProperty("file.system.trace.loading", "").split(";").filter { it.isNotBlank() }
    if (pathsToTrack.isEmpty()) {
      ourPaths = setOf()
      ourLeafNameIds = IntSets.EMPTY_SET
    }
    else {
      ourPaths = CollectionFactory.createFilePathSet(pathsToTrack, SystemInfoRt.isFileSystemCaseSensitive)
      ourLeafNameIds = IntOpenHashSet(ourPaths.size)
      val fsRecords = FSRecords.getInstance()
      for (path in ourPaths) {
        ourLeafNameIds.add(fsRecords.getNameId(StringUtil.getShortName(path, '/')))
      }
    }
  }

  @JvmStatic
  fun fileLoaded(parent: VirtualDirectoryImpl, nameId: Int) {
    if (ourLeafNameIds.contains(nameId)) {
      val path = parent.path + '/' + FSRecords.getInstance().getNameByNameId(nameId)
      if (ourPaths.contains(path)) {
        LOG.info("Loading $path", Throwable())
      }
    }
  }
}
