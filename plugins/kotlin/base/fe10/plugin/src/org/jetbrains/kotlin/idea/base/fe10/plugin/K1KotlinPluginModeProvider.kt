// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.plugin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

internal class K1KotlinPluginModeProvider : KotlinPluginModeProvider {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1
}