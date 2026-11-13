// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-resolution.spec.md

import org.jetbrains.annotations.ApiStatus.Experimental

/** Holds the ready-made values for [SettingsKey.contextFilter]. Each one tests [SettingsContext.kind]. */
@Experimental
object SettingsContextFilters {
  /** Accepts only a context of the [application kind][SettingsContextKind.APPLICATION]. */
  val APP_ONLY: (SettingsContext) -> Boolean = { it.kind == SettingsContextKind.APPLICATION }

  /** Accepts only a context of the [project kind][SettingsContextKind.PROJECT]. */
  val PROJECT_ONLY: (SettingsContext) -> Boolean = { it.kind == SettingsContextKind.PROJECT }

  /**
   * Accepts a context of the [project kind][SettingsContextKind.PROJECT] or of a wider kind, by the order of
   * [SettingsContextKind]. So a new kind that is wider than a project is accepted with no change here.
   */
  val PROJECT_OR_WIDER: (SettingsContext) -> Boolean = { it.kind <= SettingsContextKind.PROJECT }
}
