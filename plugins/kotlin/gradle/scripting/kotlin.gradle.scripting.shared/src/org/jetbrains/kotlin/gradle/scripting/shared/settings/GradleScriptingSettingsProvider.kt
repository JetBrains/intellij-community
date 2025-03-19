// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.settings

import KotlinGradleScriptingBundle
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.script.configuration.ScriptingSupportSpecificSettingsProvider

class GradleSettingsProvider(private val project: Project) : ScriptingSupportSpecificSettingsProvider() {
    override val title: String = KotlinGradleScriptingBundle.message("gradle.scripts.settings.title")

    override fun createConfigurable(): UnnamedConfigurable {
        return StandaloneScriptsUIComponent(project)
    }
}
