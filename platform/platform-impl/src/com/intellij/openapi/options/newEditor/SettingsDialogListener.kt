// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Allows listening for interaction with buttons in the Settings Dialog. Events for other buttons can be added later.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface SettingsDialogListener {
  /** Fired on 'Apply' button click, but after changed settings are applied */
  fun afterApply(settingsEditor: AbstractEditor) {
  }

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<SettingsDialogListener> = Topic(SettingsDialogListener::class.java, Topic.BroadcastDirection.NONE)
  }
}