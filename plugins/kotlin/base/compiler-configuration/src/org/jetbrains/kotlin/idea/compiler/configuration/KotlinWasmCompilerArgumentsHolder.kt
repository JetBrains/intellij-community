// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.KotlinWasmCompilerArguments
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_TO_WASM_COMPILER_ARGUMENTS_SECTION

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_TO_WASM_COMPILER_ARGUMENTS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinWasmCompilerArgumentsHolder(project: Project) : BaseKotlinCompilerSettings<KotlinWasmCompilerArguments>(project) {
    override fun createSettings(): KotlinWasmCompilerArguments = KotlinWasmCompilerArguments()

    override fun validateNewSettings(settings: KotlinWasmCompilerArguments) {
        validateInheritedFieldsUnchanged(settings)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinWasmCompilerArgumentsHolder = project.service()
    }
}
