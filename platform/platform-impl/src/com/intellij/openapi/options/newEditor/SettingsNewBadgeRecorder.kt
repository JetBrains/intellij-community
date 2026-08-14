// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SettingsNewBadgeRecorder")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableVisitor

internal const val KEY_PREFIX: String = "settings.new.badge.shown.count."
internal const val MAX_SHOWS: Int = 1

internal fun recordOpened(configurable: Configurable) {
  PropertiesComponent.getInstance().setValue(KEY_PREFIX + ConfigurableVisitor.getId(configurable), MAX_SHOWS, 0)
}

internal fun shownCount(configurable: Configurable): Int {
  return PropertiesComponent.getInstance().getInt(KEY_PREFIX + ConfigurableVisitor.getId(configurable), 0)
}
