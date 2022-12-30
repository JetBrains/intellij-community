// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.legacy

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

class GradleStandaloneScriptActions(
    val manager: GradleStandaloneScriptActionsManager,
    val file: VirtualFile,
    val isFirstLoad: Boolean,
    private val doLoad: () -> Unit
) {
    val project get() = manager.project

    private val scriptDefinition
        get() = file.findScriptDefinition(project)

    private val isAutoReloadAvailable: Boolean
    private val isAutoReloadEnabled: Boolean

    init {
        val scriptDefinition = scriptDefinition
        if (scriptDefinition != null) {
            isAutoReloadAvailable = true
            isAutoReloadEnabled = KotlinScriptingSettings.getInstance(project)
                .autoReloadConfigurations(scriptDefinition)
        } else {
            isAutoReloadAvailable = false
            isAutoReloadEnabled = false
        }
    }

    fun enableAutoReload() {
        KotlinScriptingSettings.getInstance(project).setAutoReloadConfigurations(scriptDefinition!!, true)
        doLoad()
        updateNotification()
    }

    fun reload() {
        doLoad()
        manager.remove(file)
    }

    fun updateNotification() {
        GradleBuildRootsManager.getInstance(project)?.updateNotifications(false) {
            it == file.path
        }
    }
}