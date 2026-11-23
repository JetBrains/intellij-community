// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider

internal class ScriptDefinitionsBuildListener(private val project: Project) : ProjectTaskListener {
    override fun finished(result: ProjectTaskManager.Result) {
        if (result.isAborted || result.hasErrors()) return
        val moduleWasBuilt = result.anyTaskMatches { task, state ->
            task is ModuleBuildTask && !state.isFailed && !state.isSkipped
        }
        if (moduleWasBuilt) {
            project.scheduleLoadingIfProviderCreated()
        }
    }
}

/** Covers the tasks that run outside [ProjectTaskManager], for example from the Gradle tool window. */
internal class ScriptDefinitionsExternalTaskListener : ExternalSystemTaskNotificationListener {
    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.EXECUTE_TASK) return
        id.findProject()?.scheduleLoadingIfProviderCreated()
    }
}

/** Without the provider, nothing holds the definitions, and its creation loads them. */
private fun Project.scheduleLoadingIfProviderCreated() {
    (serviceIfCreated<ScriptDefinitionProvider>() as? ScriptDefinitionProviderImpl)?.scheduleLoading()
}
