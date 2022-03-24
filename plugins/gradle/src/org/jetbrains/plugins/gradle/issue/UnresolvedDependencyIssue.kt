// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildView
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix.Companion.requestImport
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.runAsync

@ApiStatus.Internal
abstract class UnresolvedDependencyIssue(
  dependencyName: String,
  private val dependencyOwner: String? = null,
) : BuildIssue {
  override val title: String = "Could not resolve $dependencyName" + if (dependencyOwner != null) " for $dependencyOwner" else ""

  override fun getNavigatable(project: Project): Navigatable? = null

  fun buildDescription(failureMessage: String?, isOfflineMode: Boolean, offlineModeQuickFixText: String): String {
    val issueDescription = StringBuilder()
    if(dependencyOwner != null) {
      issueDescription.append(dependencyOwner)
      issueDescription.append(": ")
    }

    issueDescription.append(failureMessage?.trim())
    val noRepositoriesDefined = failureMessage?.contains("no repositories are defined") ?: false

    issueDescription.append("\n\nPossible solution:\n")
    when {
      isOfflineMode && !noRepositoriesDefined -> issueDescription.append(
        " - <a href=\"$offlineQuickFixId\">$offlineModeQuickFixText</a>\n")
      else -> issueDescription.append(
        " - Declare repository providing the artifact, see the documentation at $declaringRepositoriesLink\n")
    }
    return issueDescription.toString()
  }

  companion object {
    internal const val offlineQuickFixId = "disable_offline_mode"
    private const val declaringRepositoriesLink = "https://docs.gradle.org/current/userguide/declaring_repositories.html"
  }
}

@ApiStatus.Experimental
data class UnresolvedDependencySyncIssue @JvmOverloads constructor(
  private val dependencyName: String,
  private val failureMessage: String?,
  private val projectPath: String,
  private val isOfflineMode: Boolean,
  private val dependencyOwner: String? = null,
) : UnresolvedDependencyIssue(dependencyName, dependencyOwner) {
  override val quickFixes = if (isOfflineMode) listOf<BuildIssueQuickFix>(DisableOfflineAndReimport(projectPath)) else emptyList()
  override val description: String = buildDescription(failureMessage, isOfflineMode, "Disable offline mode and reload the project")

  inner class DisableOfflineAndReimport(private val projectPath: String) : BuildIssueQuickFix {
    override val id = offlineQuickFixId
    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      GradleSettings.getInstance(project).isOfflineWork = false
      return tryRerun(dataContext) ?: requestImport(project, projectPath, GradleConstants.SYSTEM_ID)
    }
  }
}

@ApiStatus.Experimental
class UnresolvedDependencyBuildIssue(dependencyName: String,
                                     failureMessage: String?,
                                     isOfflineMode: Boolean) : UnresolvedDependencyIssue(dependencyName) {
  override val quickFixes = if (isOfflineMode) listOf<BuildIssueQuickFix>(DisableOfflineAndRerun()) else emptyList()
  override val description: String = buildDescription(failureMessage, isOfflineMode, "Disable offline mode and rerun the build")

  inner class DisableOfflineAndRerun : BuildIssueQuickFix {
    override val id = offlineQuickFixId
    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      GradleSettings.getInstance(project).isOfflineWork = false
      return tryRerun(dataContext) ?: CompletableFuture.completedFuture(null)
    }
  }
}

private fun tryRerun(dataContext: DataContext): CompletableFuture<*>? {
  val environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(dataContext)
  if (environment != null) {
    return runAsync { ExecutionUtil.restart(environment) }
  }
  val restartActions = BuildView.RESTART_ACTIONS.getData(dataContext)
  val reimportActionText = ExternalSystemBundle.message("action.refresh.project.text", GradleConstants.SYSTEM_ID.readableName)
  restartActions?.find { it.templateText == reimportActionText }?.let { action ->
    val actionEvent = AnActionEvent.createFromAnAction(action, null, "BuildView", dataContext)
    action.update(actionEvent)
    if (actionEvent.presentation.isEnabledAndVisible) {
      return runAsync { action.actionPerformed(actionEvent) }
    }
  }
  return null
}