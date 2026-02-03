// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener

class KotlinGlobalSettingsChangeListener(val project: Project): KotlinCompilerSettingsListener {
    override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
        // Clear currently resident JPS processes on any kotlin compiler settings changes
        // this includes changes in org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
        if (!project.isDefault) {
            BuildManager.getInstance().clearState(project)
        }
    }
}