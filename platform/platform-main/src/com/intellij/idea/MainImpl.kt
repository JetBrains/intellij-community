// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Deferred

internal class MainImpl : AppStarter {
  init {
    PlatformUtils.setDefaultPrefixForCE()
  }

  override suspend fun start(args: List<String>, appDeferred: Deferred<Any>) {
    initApplication(args, appDeferred)
  }
}