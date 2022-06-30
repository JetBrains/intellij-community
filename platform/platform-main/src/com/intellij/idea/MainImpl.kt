// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Deferred
import java.util.concurrent.CompletableFuture

class MainImpl : AppStarter {
  init {
    PlatformUtils.setDefaultPrefixForCE()
  }

  override fun start(args: List<String>, prepareUiFuture: Deferred<Any>): CompletableFuture<*> {
    initApplication(args, prepareUiFuture)
    return CompletableFuture.completedFuture(null)
  }
}