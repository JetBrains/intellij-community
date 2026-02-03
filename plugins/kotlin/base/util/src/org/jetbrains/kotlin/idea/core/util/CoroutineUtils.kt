// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job

// job that is cancelled when the project is disposed
val Project.cancelOnDisposal: Job
    get() = this.service<ProjectJob>().sharedJob

@Service(Service.Level.PROJECT)
internal class ProjectJob(project: Project) : Disposable {
    internal val sharedJob: Job = Job()

    override fun dispose() {
        sharedJob.cancel()
    }
}
