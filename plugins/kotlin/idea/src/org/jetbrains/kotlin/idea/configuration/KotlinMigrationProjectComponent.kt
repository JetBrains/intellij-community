// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.runUnderDisposeAwareIndicator

class KotlinMigrationProjectComponent : StartupActivity {

    override fun runActivity(project: Project) {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val connection = project.messageBus.connect(disposable)
        connection.subscribe(ProjectDataImportListener.TOPIC, ProjectDataImportListener {
            runUnderDisposeAwareIndicator(disposable) {
                KotlinMigrationProjectService.getInstance(project).onImportFinished()
            }
        })
    }

}
