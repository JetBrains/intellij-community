// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import KotlinGradleScriptingBundle
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeature

internal class GradleScriptConfigurationsImportingFeature : ExperimentalFeature() {
    override val title: String
        get() = KotlinGradleScriptingBundle.message("gradle.script.configurations.importing.feature")

    override fun shouldBeShown(): Boolean = true

    override var isEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                ProjectManager.getInstance().openProjects.forEach {
                    GradleBuildRootsManager.getInstanceSafe(it).enabled = field
                }
            }
        }
}