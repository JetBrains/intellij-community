// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.ide.bootstrap.InitAppContext
import com.intellij.platform.ide.bootstrap.AppStarter
import com.intellij.platform.ide.bootstrap.initApplication
import com.intellij.util.PlatformUtils

internal class MainImpl : AppStarter {
  init {
    PlatformUtils.setDefaultPrefixForCE()
  }

  override suspend fun start(context: InitAppContext) {
    initApplication(context)
  }
}