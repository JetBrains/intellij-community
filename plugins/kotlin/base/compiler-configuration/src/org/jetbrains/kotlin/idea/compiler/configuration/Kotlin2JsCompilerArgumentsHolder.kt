// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_TO_JS_COMPILER_ARGUMENTS_SECTION

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_TO_JS_COMPILER_ARGUMENTS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class Kotlin2JsCompilerArgumentsHolder(project: Project) : BaseKotlinCompilerSettings<K2JSCompilerArguments>(project) {
    override fun createSettings() = K2JSCompilerArguments()

    override fun validateNewSettings(settings: K2JSCompilerArguments) {
        validateInheritedFieldsUnchanged(settings)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): Kotlin2JsCompilerArgumentsHolder = project.service()
    }
}
