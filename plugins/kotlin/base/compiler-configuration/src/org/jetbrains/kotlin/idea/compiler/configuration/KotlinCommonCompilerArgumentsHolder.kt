// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.setApiVersionToLanguageVersionIfNeeded
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_COMMON_COMPILER_ARGUMENTS_SECTION

@Service(Service.Level.PROJECT)
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

    fun updateLanguageAndApi(project: Project, modules: Array<Module>? = null) {
        val kotlinFacetSettingsProvider = KotlinFacetSettingsProvider.getInstance(project)
        val languageVersions = linkedSetOf<LanguageVersion>()
        val apiVersions = linkedSetOf<LanguageVersion>()
        for (module in modules ?: ModuleManager.getInstance(project).modules) {
            if (module.isDisposed) continue
            val settings = kotlinFacetSettingsProvider?.getSettings(module) ?: continue
            if (settings.useProjectSettings) continue
            settings.languageLevel?.let(languageVersions::add)
            settings.apiLevel?.let(apiVersions::add)
        }

        val languageVersion = languageVersions.singleOrNull()
        val apiVersion = apiVersions.singleOrNull()
        update {
            this.languageVersion = languageVersion?.versionString
            this.apiVersion = apiVersion?.versionString
        }
    }

    override fun createSettings() = CommonCompilerArguments.DummyImpl()

    companion object {
        /**
         * @see org.jetbrains.kotlin.idea.facet.getInstance
         */
        @JvmStatic
        fun getInstance(project: Project): KotlinCommonCompilerArgumentsHolder = project.service()
    }
}

fun isKotlinLanguageVersionConfigured(arguments: KotlinCommonCompilerArgumentsHolder): Boolean {
    val settings = arguments.settings
    return settings.languageVersion != null && settings.apiVersion != null
}

fun isKotlinLanguageVersionConfigured(project: Project): Boolean {
    return isKotlinLanguageVersionConfigured(KotlinCommonCompilerArgumentsHolder.getInstance(project))
}