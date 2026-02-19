// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_COMPILER_SETTINGS_SECTION

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_COMPILER_SETTINGS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinCompilerSettings(project: Project) : BaseKotlinCompilerSettings<CompilerSettings>(project) {
    override fun createSettings() = CompilerSettings()

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinCompilerSettings = project.service()
    }
}
