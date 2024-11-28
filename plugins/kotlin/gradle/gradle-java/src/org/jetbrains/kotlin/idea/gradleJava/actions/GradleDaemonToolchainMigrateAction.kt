// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.gradleJava.toolchain.GradleDaemonToolchainMigrationService
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleDaemonToolchainMigrateAction : ExternalSystemAction() {

    override fun isEnabled(e: AnActionEvent) = Registry.`is`("gradle.daemon.jvm.criteria.suggest.migration")

    override fun isVisible(e: AnActionEvent) = Registry.`is`("gradle.daemon.jvm.criteria.suggest.migration")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        GradleSettings.getInstance(project)
            .linkedProjectsSettings
            .mapNotNull { it.externalProjectPath }
            .forEach {
                val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(it) ?: return@forEach
                if (!GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(projectSettings.resolveGradleVersion())) return@forEach
                if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(projectSettings)) return@forEach
                GradleDaemonToolchainMigrationService(project).startMigration(it)
            }
    }
}