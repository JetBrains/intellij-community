// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.project.stateStore
import com.intellij.util.io.exists
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_JPS_PLUGIN_SETTINGS_SECTION
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

@State(name = KOTLIN_JPS_PLUGIN_SETTINGS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinJpsPluginSettings(project: Project) : BaseKotlinCompilerSettings<JpsPluginSettings>(project) {
    override fun createSettings() = JpsPluginSettings()

    companion object {
        // Use bundled by default because this will work even without internet connection
        @JvmField
        val DEFAULT_VERSION = KotlinPluginLayout.instance.standaloneCompilerVersion.rawVersion

        fun validateSettings(project: Project) {
            val jpsPluginSettings = project.service<KotlinJpsPluginSettings>()
            if (!isUnbundledJpsExperimentalFeatureEnabled(project)) {
                // Delete compiler version in kotlinc.xml when feature flag is off
                jpsPluginSettings.update { version = "" }
                return
            }

            if (jpsPluginSettings.settings.version.isEmpty()) {
                // Encourage user to specify desired Kotlin compiler version in project settings for sake of reproducible builds
                jpsPluginSettings.update { version = DEFAULT_VERSION }
            }
        }

        fun getJpsVersion(project: Project): String? {
            return getInstanceUnsafe(project)?.settings?.version?.ifEmpty { DEFAULT_VERSION }
        }

        /**
         * [getInstanceUnsafe] is needed for cases when:
         * * it's important to preserve "not initialized" state
         * * it's important not to trigger `.idea/kotlinc.xml` file creation
         *
         * @return "bare" [JpsPluginSettings]
         */
        fun getInstanceUnsafe(project: Project): KotlinJpsPluginSettings? =
            if (isUnbundledJpsExperimentalFeatureEnabled(project)) project.service() else null

        fun isUnbundledJpsExperimentalFeatureEnabled(project: Project): Boolean =
            isUnitTestMode() || !project.isDefault &&
                    project.stateStore.directoryStorePath?.resolve("kotlin-unbundled-jps-experimental-feature-flag")?.exists() == true
    }
}

@get:NlsSafe
val JpsPluginSettings.versionWithFallback: String get() = version.ifEmpty { KotlinJpsPluginSettings.DEFAULT_VERSION }
