// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.UpdateChecker.excludedFromUpdateCheckPlugins

internal open class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        excludedFromUpdateCheckPlugins.add("org.jetbrains.kotlin")

        executeExtraActions(project)
    }

    protected open suspend fun executeExtraActions(project: Project) = Unit
}