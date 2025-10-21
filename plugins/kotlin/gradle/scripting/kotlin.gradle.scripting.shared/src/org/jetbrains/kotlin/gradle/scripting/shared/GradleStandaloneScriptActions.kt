// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettingsStorage
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

class GradleStandaloneScriptActions(
    val manager: GradleStandaloneScriptActionsManager,
    val file: VirtualFile,
    val isFirstLoad: Boolean,
    private val doLoad: () -> Unit
) {
    val project: Project get() = manager.project

    private val scriptDefinition
        get() = file.findScriptDefinition(project)

    private val isAutoReloadAvailable: Boolean
    private val isAutoReloadEnabled: Boolean

    init {
        val scriptDefinition = scriptDefinition
        if (scriptDefinition != null) {
            isAutoReloadAvailable = true
            isAutoReloadEnabled = KotlinScriptingSettingsStorage.getInstance(project)
                .autoReloadConfigurations(scriptDefinition)
        } else {
            isAutoReloadAvailable = false
            isAutoReloadEnabled = false
        }
    }

    fun enableAutoReload() {
        KotlinScriptingSettingsStorage.getInstance(project).setAutoReloadConfigurations(scriptDefinition!!, true)
        doLoad()
        updateNotification()
    }

    fun reload() {
        doLoad()
        manager.remove(file)
    }

    fun updateNotification() {
        GradleBuildRootsLocator.getInstance(project)?.updateNotifications(false) {
            it == file.path
        }
    }
}