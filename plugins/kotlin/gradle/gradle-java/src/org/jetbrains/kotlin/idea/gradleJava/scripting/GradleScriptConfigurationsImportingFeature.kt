// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeature
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager

internal class GradleScriptConfigurationsImportingFeature : ExperimentalFeature() {
    override val title: String
        get() = KotlinIdeaGradleBundle.message("gradle.script.configurations.importing.feature")

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