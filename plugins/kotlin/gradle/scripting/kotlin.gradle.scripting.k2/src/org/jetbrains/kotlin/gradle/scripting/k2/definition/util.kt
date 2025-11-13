// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.definition

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.scripting.k2.GradleKotlinScriptService
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.k2.configurations.configurationProviderExtension
import org.jetbrains.kotlin.idea.core.script.v1.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import kotlin.script.experimental.api.ide


fun GradleScriptDefinition.withIdeKeys(project: Project): GradleScriptDefinition = with {
    ide {
        kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
            id = "gradle-kts"
            title = ".gradle.kts"
            templateName = "Kotlin Script Gradle"
        })
        configurationProviderExtension {
            GradleKotlinScriptService.getInstance(project)
        }
    }
}