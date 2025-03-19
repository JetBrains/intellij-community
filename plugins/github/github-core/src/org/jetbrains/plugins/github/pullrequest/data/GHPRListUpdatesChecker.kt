// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface GHPRListUpdatesChecker : Disposable {

  @get:RequiresEdt
  val outdated: Boolean

  @RequiresEdt
  fun start()

  @RequiresEdt
  fun stop()

  @RequiresEdt
  fun addOutdatedStateChangeListener(disposable: Disposable, listener: () -> Unit)
}
