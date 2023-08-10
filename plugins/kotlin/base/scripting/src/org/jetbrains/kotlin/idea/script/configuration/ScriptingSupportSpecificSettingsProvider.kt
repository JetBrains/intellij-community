// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.Nls

abstract class ScriptingSupportSpecificSettingsProvider {

    @get:Nls
    abstract val title: String

    abstract fun createConfigurable(): UnnamedConfigurable

    companion object {
        @JvmField
        val SETTINGS_PROVIDERS: ExtensionPointName<ScriptingSupportSpecificSettingsProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.scripting.idea.settings.provider")
    }
}

