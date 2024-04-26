// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_TO_JVM_COMPILER_ARGUMENTS_SECTION

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_TO_JVM_COMPILER_ARGUMENTS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class Kotlin2JvmCompilerArgumentsHolder(project: Project) : BaseKotlinCompilerSettings<K2JVMCompilerArguments>(project) {
    override fun createSettings() = K2JVMCompilerArguments()

    override fun validateNewSettings(settings: K2JVMCompilerArguments) {
        validateInheritedFieldsUnchanged(settings)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): Kotlin2JvmCompilerArgumentsHolder = project.service()
    }
}
