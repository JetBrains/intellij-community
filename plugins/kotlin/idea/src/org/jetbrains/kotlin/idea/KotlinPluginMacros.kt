// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.PathMacroContributor
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

/**
 * Some actions have to be performed before loading and opening any project.
 *
 * E.g. path variables have to be registered in advance as modules could rely on some path variables.
 */
class KotlinPluginMacros : PathMacroContributor {
    override fun registerPathMacros(macros: MutableMap<String, String>, legacyMacros: MutableMap<String, String>) {

    }

    override fun forceRegisterPathMacros(macros: MutableMap<String, String>) {
        if (!isUnitTestMode()) {
            macros[KOTLIN_BUNDLED_PATH_VARIABLE] = KotlinPluginLayout.getInstance().kotlinc.canonicalPath
        }
    }

    companion object {
        const val KOTLIN_BUNDLED_PATH_VARIABLE = "KOTLIN_BUNDLED"
    }

}