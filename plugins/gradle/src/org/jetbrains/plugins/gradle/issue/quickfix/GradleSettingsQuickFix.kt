// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.function.BiPredicate

@ApiStatus.Experimental
class GradleSettingsQuickFix(private val myProjectPath: String, private val myRequestImport: Boolean,
                             private val myConfigurationChangeDetector: BiPredicate<GradleProjectSettings, GradleProjectSettings>?,
                             private val myFilter: String?) : BuildIssueQuickFix {

  override val id: String = "fix_gradle_settings"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(myProjectPath)
                          ?: return CompletableFuture.completedFuture(false)
    val future = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      val oldSettings: GradleProjectSettings?
      oldSettings = projectSettings.clone()

      val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
      val configurable = ConfigurableVisitor.findByType(GradleConfigurable::class.java, groups.toList())
      val dialogWrapper = SettingsDialogFactory.getInstance().create(project, groups, configurable, myFilter)
      val result = dialogWrapper.showAndGet()
      future.complete(result && myConfigurationChangeDetector != null && myConfigurationChangeDetector.test(oldSettings, projectSettings))
    }
    return future.thenCompose { isSettingsChanged ->
      if (isSettingsChanged!! && myRequestImport)
        ExternalSystemUtil.requestImport(project, myProjectPath, GradleConstants.SYSTEM_ID)
      else
        CompletableFuture.completedFuture(null)
    }
  }

  object GradleJvmChangeDetector : BiPredicate<GradleProjectSettings, GradleProjectSettings> {
    override fun test(t: GradleProjectSettings, u: GradleProjectSettings): Boolean {
      return t.gradleJvm != u.gradleJvm
    }
  }
}
