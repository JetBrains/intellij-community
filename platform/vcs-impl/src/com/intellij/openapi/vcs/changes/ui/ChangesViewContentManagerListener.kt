// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic

interface ChangesViewContentManagerListener {
  @RequiresEdt
  fun toolWindowMappingChanged()

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ChangesViewContentManagerListener> =
      Topic(ChangesViewContentManagerListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}