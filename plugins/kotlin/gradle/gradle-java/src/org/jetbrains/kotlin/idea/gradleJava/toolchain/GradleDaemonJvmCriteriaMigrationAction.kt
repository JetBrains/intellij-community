// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.toolchain

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path

class GradleDaemonJvmCriteriaMigrationAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = Registry.Companion.`is`("gradle.daemon.jvm.criteria.suggest.migration") && e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!Registry.`is`("gradle.daemon.jvm.criteria.suggest.migration")) return
        val project = e.project ?: return
        val settings = GradleSettings.getInstance(project)
        for (projectSettings in settings.linkedProjectsSettings) {
            val projectPath = projectSettings.externalProjectPath
            val gradleVersion = projectSettings.resolveGradleVersion()
            if (GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(gradleVersion)) {
                if (!GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(Path.of(projectPath), gradleVersion)) {
                    GradleDaemonJvmCriteriaMigrationHelper.migrateToDaemonJvmCriteria(project, projectPath)
                }
            }
        }
    }
}