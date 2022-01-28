// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.project.stateStore
import com.intellij.util.io.exists
import org.jetbrains.kotlin.cli.common.arguments.unfrozen
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_JPS_PLUGIN_SETTINGS_SECTION
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

@State(name = KOTLIN_JPS_PLUGIN_SETTINGS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinJpsPluginSettings(project: Project) : BaseKotlinCompilerSettings<JpsPluginSettings>(project) {
    override fun createSettings() = JpsPluginSettings()

    companion object {
        fun getInstance(project: Project) =
            project.getServiceSafe<KotlinJpsPluginSettings>()
                .also {
                    if (!isUnbundledJpsExperimentalFeatureEnabled(project)) {
                        // Delete compiler version in kotlinc.xml when feature flag is off
                        it.settings = it.settings.unfrozen().apply { version = "" }
                    }
                }
                .takeIf { isUnbundledJpsExperimentalFeatureEnabled(project) }
                ?.also {
                    if (it.settings.version.isEmpty()) {
                        // Encourage user to specify desired Kotlin compiler version in project settings for sake of reproducible builds
                        it.settings = it.settings.unfrozen().apply {
                            // Use bundled by default because this will work even without internet connection
                            version = KotlinCompilerVersion.VERSION
                        }
                    }
                }

        fun isUnbundledJpsExperimentalFeatureEnabled(project: Project): Boolean =
            project.stateStore.directoryStorePath?.resolve("kotlin-unbundled-jps-experimental-feature-flag")?.exists() == true
    }
}
