// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise

internal class ScriptDefinitionsBuildListener(private val project: Project) : ProjectTaskListener {
    override fun finished(result: ProjectTaskManager.Result) {
        if (result.isAborted || result.hasErrors()) return
        val moduleWasBuilt = result.anyTaskMatches { task, state ->
            task is ModuleBuildTask && !state.isFailed && !state.isSkipped
        }
        if (moduleWasBuilt) {
            refreshDefinitions()
        }
    }

    /** Reloads the definitions only when the build changed the marker files. */
    internal fun refreshDefinitions(): CancellablePromise<Unit> =
        ReadAction.nonBlocking<Unit> {
            if (ScriptTemplatesFromDependenciesCache.isOutdated(project)) {
                ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
            }
        }
            .expireWhen { project.isDisposed }
            .submit(AppExecutorUtil.getAppExecutorService())
}
