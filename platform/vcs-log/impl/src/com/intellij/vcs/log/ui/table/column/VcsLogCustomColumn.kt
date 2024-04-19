// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.util.*

/**
 * Extension point provides a way to add a new column to VCS Log (e.g. Build Status, Attached Reviews, Commit Verification Status)
 *
 * @see VcsLogColumn for more details about column customization
 */
interface VcsLogCustomColumn<T> : VcsLogColumn<T> {

  /**
   * @return [true] if column should be visible by default. [false] if column should be hidden.
   *
   * It is possible to show/hide column under "Eye" icon -> Show Columns -> [localizedName]
   */
  fun isEnabledByDefault() = true

  /**
   * Allow to disable non-applicable columns.
   *
   * @see VcsLogCustomColumnListener.columnAvailabilityChanged
   */
  fun isAvailable(project: Project): Boolean = true

  companion object {
    @JvmField
    val KEY = ExtensionPointName<VcsLogCustomColumn<*>>("com.intellij.vcsLogCustomColumn")
  }
}

interface VcsLogCustomColumnListener : EventListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic(VcsLogCustomColumnListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /**
   * Allows notifying VCS Log that the [VcsLogCustomColumn.isAvailable] might have changed.
   */
  fun columnAvailabilityChanged() = Unit
}
