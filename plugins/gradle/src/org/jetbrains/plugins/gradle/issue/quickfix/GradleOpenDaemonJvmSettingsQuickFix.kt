// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.concurrent.CompletableFuture

object GradleOpenDaemonJvmSettingsQuickFix : BuildIssueQuickFix {

    override val id: String = "open_daemon_jvm_criteria_settings"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
        ApplicationManager.getApplication().invokeLater {
            val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
            val configurable = ConfigurableVisitor.findByType(GradleConfigurable::class.java, groups.toList())
            val gradleJvmSettingsFilter = GradleBundle.message("gradle.settings.text.jvm.path")
            val dialogWrapper = SettingsDialogFactory.getInstance().create(project, groups, configurable, gradleJvmSettingsFilter)
            dialogWrapper.show()
        }
        return CompletableFuture.completedFuture(null)
    }
}