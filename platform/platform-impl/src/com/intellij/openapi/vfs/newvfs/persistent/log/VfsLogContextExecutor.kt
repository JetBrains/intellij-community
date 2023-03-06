// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import kotlinx.coroutines.Job

interface VfsLogContextExecutor {
  fun launch(action: suspend VfsLog.Context.() -> Unit): Job

  fun run(action: VfsLog.Context.() -> Unit)
}