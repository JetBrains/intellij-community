// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettingsCustomDataSynchronizer
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings

class KotlinCodeStyleSettingsCustomDataSynchronizer : CustomCodeStyleSettingsCustomDataSynchronizer<KotlinCodeStyleSettings>() {
  override val id get() = "CustomCodeStyleSettings_KOTLIN"

  override val customCodeStyleSettingsClass get() = KotlinCodeStyleSettings::class.java

  override val defaultSettings get() = KotlinCodeStyleSettings(CodeStyleSettings.getDefaults())
}