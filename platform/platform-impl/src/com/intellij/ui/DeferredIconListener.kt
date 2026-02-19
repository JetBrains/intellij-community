// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface DeferredIconListener {
  fun evaluated(deferred: DeferredIcon, result: Icon)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<DeferredIconListener> = Topic(DeferredIconListener::class.java, BroadcastDirection.NONE)
  }
}