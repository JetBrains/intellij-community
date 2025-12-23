// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefs

open class DataPackBase(
  val logProviders: Map<VirtualFile, VcsLogProvider>,
  val refsModel: VcsLogRefs,
  val isFull: Boolean,
)
