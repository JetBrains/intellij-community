// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic

interface ChangesViewContentManagerListener {
  @RequiresEdt
  fun toolWindowMappingChanged()

  companion object {
    @JvmField
    val TOPIC: Topic<ChangesViewContentManagerListener> =
      Topic(ChangesViewContentManagerListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}