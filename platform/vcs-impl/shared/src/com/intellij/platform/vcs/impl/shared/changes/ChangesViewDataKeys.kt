// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ChangesViewDataKeys {
  @JvmField
  val SETTINGS: DataKey<ChangesViewSettings> = DataKey.create("CHANGES_VIEW_SETTINGS")

  @JvmField
  val REFRESHER: DataKey<Runnable> = DataKey.create("CHANGES_VIEW_REFERESHER")
}