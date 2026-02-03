// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.openapi.util.NlsContexts.DetailedDescription

internal class KotlinPluginKindAboutPopupDescriptionProvider : AboutPopupDescriptionProvider {

    override fun getDescription(): @DetailedDescription String? {
        return when (val pluginKind = KotlinPluginModeProvider.currentPluginMode) {
            KotlinPluginMode.K2 -> null
            KotlinPluginMode.K1 -> KotlinBasePluginBundle.message("kotlin.plugin.kind.text", pluginKind.pluginModeDescription)
        }
    }
}