// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ChangesBrowserNodeKeys {
  /** When set to true on a ChangesBrowserNode, the inclusion checkbox should be hidden for that node. */
  @JvmField
  val HIDE_INCLUSION_CHECKBOX: Key<Boolean> = Key.create("changes.node.hide.checkbox")

  /**
   * When set to true on a ChangesBrowserNode, selection controllers may treat the node as non-selectable.
   */
  @JvmField
  val NON_SELECTABLE: Key<Boolean> = Key.create("changes.node.non.selectable")
}
