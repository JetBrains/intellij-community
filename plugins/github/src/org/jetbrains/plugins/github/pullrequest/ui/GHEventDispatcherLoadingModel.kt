// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.util.EventDispatcher

abstract class GHEventDispatcherLoadingModel : GHLoadingModel {
  protected val eventDispatcher = EventDispatcher.create(GHLoadingModel.StateChangeListener::class.java)

  final override fun addStateChangeListener(listener: GHLoadingModel.StateChangeListener) = eventDispatcher.addListener(listener)

  fun removeStateChangeListener(listener: GHLoadingModel.StateChangeListener) = eventDispatcher.removeListener(listener)
}