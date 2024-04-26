// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings

class KotlinCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<KotlinCodeStyleSettings>() {
    override val language get() = KotlinLanguage.INSTANCE

    override val customCodeStyleSettingsClass get() = KotlinCodeStyleSettings::class.java
}