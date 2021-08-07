// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.setApiVersionToLanguageVersionIfNeeded
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_COMMON_COMPILER_ARGUMENTS_SECTION
import org.jetbrains.kotlin.config.detectVersionAutoAdvance
import org.jetbrains.kotlin.config.dropVersionsIfNecessary
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

@State(name = KOTLIN_COMMON_COMPILER_ARGUMENTS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinCommonCompilerArgumentsHolder(project: Project) : BaseKotlinCompilerSettings<CommonCompilerArguments>(project) {
    override fun getState(): Element {
        return super.getState().apply {
            dropVersionsIfNecessary(settings)
        }
    }

    override fun loadState(state: Element) {
        super.loadState(state)

        update {
            // To fix earlier configurations with incorrect combination of language and API version
            setApiVersionToLanguageVersionIfNeeded()
            detectVersionAutoAdvance()
        }
    }

    override fun createSettings() = CommonCompilerArguments.DummyImpl()

    companion object {
        fun getInstance(project: Project): KotlinCommonCompilerArgumentsHolder = project.getServiceSafe()
    }
}
