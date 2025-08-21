// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.util.messages.Topic

internal interface ChangesListManagerStateListener {
  companion object {
    @JvmField
    val TOPIC: Topic<ChangesListManagerStateListener> =
      Topic.create("ChangesListManagerStateListener", ChangesListManagerStateListener::class.java)

    fun adapter(statusChanged: () -> Unit): ChangesListManagerStateListener = object : ChangesListManagerStateListener {
      override fun changesListManagerUnfrozen() = statusChanged()
      override fun changesListManagerFrozen() = statusChanged()
      override fun updateStarted() = statusChanged()
      override fun updateFinished() = statusChanged()
    }
  }

  fun changesListManagerFrozen()

  fun changesListManagerUnfrozen()

  fun updateStarted()

  fun updateFinished()
}