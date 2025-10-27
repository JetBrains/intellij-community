// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.scripting.k2.definition.GradleScriptDefinitionsSource
import org.jetbrains.kotlin.idea.base.highlighting.KotlinScriptHighlightingExtension
import org.jetbrains.kotlin.idea.core.script.shared.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtFile

private const val GRADLE_KTS = ".gradle.kts"

class GradleScriptHighlightingExtension(val project: Project) : KotlinScriptHighlightingExtension {
    override fun shouldHighlightScript(file: KtFile): Boolean {
        if (!file.name.endsWith(GRADLE_KTS) || isUnitTestMode()) return true

        // gradle definitions are empty so project import wasn't finished yet
        return project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions.orEmpty().any()
    }
}