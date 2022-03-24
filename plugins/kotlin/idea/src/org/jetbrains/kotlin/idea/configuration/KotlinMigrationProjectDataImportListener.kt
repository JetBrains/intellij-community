// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.runUnderDisposeAwareIndicator

class KotlinMigrationProjectDataImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
            KotlinMigrationProjectService.getInstance(project).onImportFinished()
        }
    }
}
