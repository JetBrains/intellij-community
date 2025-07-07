// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileSystemApi.WatchedPath
import com.intellij.platform.eel.path.EelPath

internal class EelData(val descriptor: EelDescriptor) {

  val recursive: MutableSet<EelPath> = mutableSetOf()
  val flat: MutableSet<EelPath> = mutableSetOf()

  fun reload(other: EelData) {
    recursive.clear()
    recursive.addAll(other.recursive)
    flat.clear()
    flat.addAll(other.flat)
  }

  fun getWatchedPaths(): Set<WatchedPath> {
    return recursive.map { eelPath -> WatchedPath.from(eelPath).recursive() }
      .plus(flat.map { eelPath -> WatchedPath.from(eelPath) })
      .toSet()
  }
}