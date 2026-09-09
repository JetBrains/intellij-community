// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.gradle.scripting.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker

/**
 * Mark the definitions cache of [ScriptDefinitionProviderImpl] dirty when a Gradle sync changes the [GradleScriptDefinitionEntity] set.
 */
internal class GradleWorkspaceModelListener(private val project: Project) : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
        if (event.getChanges(GradleScriptDefinitionEntity::class.java).any()) {
            ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        }
    }
}
