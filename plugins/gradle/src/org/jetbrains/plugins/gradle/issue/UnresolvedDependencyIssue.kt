// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildView
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.runAsync

@ApiStatus.Internal
abstract class UnresolvedDependencyIssue(
  dependencyName: String,
  private val dependencyOwner: String? = null,
) : ConfigurableGradleBuildIssue() {

  init {
    val title =
      if (dependencyOwner == null) GradleBundle.message("gradle.build.issue.unresolved.dependency.title", dependencyName)
      else GradleBundle.message("gradle.build.issue.unresolved.dependency.for.owner.title", dependencyName, dependencyOwner)
    setTitle(title)
  }

  override fun getNavigatable(project: Project): Navigatable? = null

  fun buildDescription(failureMessage: String?): @NlsSafe String {
    val issueDescription = StringBuilder()
    if(dependencyOwner != null) {
      issueDescription.append(dependencyOwner)
      issueDescription.append(": ")
    }

    val cleanedFailureMessage = when {
      JVM_VERSION_ISSUE_PATTERN_6_4.find(failureMessage ?: "") != null ->
        failureMessage?.replace(JVM_VERSION_ISSUE_PATTERN_6_4_CLEANER, "")?.trim()

      JVM_VERSION_ISSUE_PATTERN_6_2.find(failureMessage ?: "") != null ->
        failureMessage?.replace(JVM_VERSION_ISSUE_PATTERN_6_2_CLEANER, "")?.trim()

      // Gradle 6.0-6.1 JUnit 6 specific fix
      failureMessage?.startsWith(JVM_VERSION_ISSUE_JUNIT6_START) == true -> {
        val currentGradleJVM = JVM_VERSION_ISSUE_PATTERN_6_0.find(failureMessage)?.groupValues[1]?.toInt()
        GradleBundle.message("gradle.build.issue.unresolved.dependency.junit6.gradle6.description", currentGradleJVM)
      }

      else -> failureMessage?.trim()
    }

    issueDescription.append(cleanedFailureMessage)
    return issueDescription.toString()
  }

  fun configureQuickFix(projectPath: String?, failureMessage: String?, isOfflineMode: Boolean, @Nls offlineModeQuickFixText: String) {
    val noRepositoriesDefined = failureMessage?.contains("no repositories are defined") ?: false
    val requiredGradleJVM = if (projectPath != null && failureMessage != null) {
      JVM_VERSION_ISSUE_PATTERN_8_8.find(failureMessage)?.groupValues[1]?.toInt()
      ?: JVM_VERSION_ISSUE_PATTERN_6_4.find(failureMessage)?.groupValues[2]?.toInt()
      ?: JVM_VERSION_ISSUE_PATTERN_6_2.find(failureMessage)?.groupValues[2]?.toInt()
      ?: failureMessage.startsWith(JVM_VERSION_ISSUE_JUNIT6_START).let { if (it) 17 else null }
    }
    else null

    when {
      requiredGradleJVM != null -> {
        val javaVersion = JavaVersion.compose(requiredGradleJVM, 0, 0)
        addGradleJvmOrNewerQuickFix(projectPath!!, javaVersion)
      }
      isOfflineMode && !noRepositoriesDefined -> addQuickFixPrompt(offlineModeQuickFixText)
      else -> addQuickFixPrompt(
        GradleBundle.message("gradle.build.quick.fix.artifact.declare.repository", declaringRepositoriesLink)
      )
    }
  }

  private fun addGradleJvmOrNewerQuickFix(projectPath: String, javaVersion: JavaVersion) {
    // Android Studio doesn't have Gradle JVM setting
    if ("AndroidStudio" == PlatformUtils.getPlatformPrefix()) return

    val quickFix = GradleSettingsQuickFix(
      projectPath, true,
      GradleSettingsQuickFix.GradleJvmChangeDetector,
      GradleBundle.message("gradle.settings.text.jvm.path")
    )
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.jvm.or.newer", hyperlinkReference, javaVersion))
  }

  companion object {
    internal const val offlineQuickFixId = "disable_offline_mode"
    private const val declaringRepositoriesLink = "https://docs.gradle.org/current/userguide/declaring_repositories.html"
    private val JVM_VERSION_ISSUE_PATTERN_8_8 = Regex("""is only compatible with JVM runtime version (\d+) or newer\.""")
    private val JVM_VERSION_ISSUE_PATTERN_6_4 = Regex(
      """Incompatible because this component declares a component( for use during compile-time)?,? compatible with Java (\d+) """ +
      """and the consumer needed a component( for use during runtime)?,? compatible with Java (\d+)"""
    )
    private val JVM_VERSION_ISSUE_PATTERN_6_4_CLEANER = Regex(
      """\n {6}- Other compatible attributes?:[^\n]*(\n {10}[^\n]*)*"""
    )
    private val JVM_VERSION_ISSUE_PATTERN_6_2 = Regex(
      """Required org\.gradle\.jvm\.version '(\d+)' and found incompatible value '(\d+)'\."""
    )
    private val JVM_VERSION_ISSUE_PATTERN_6_2_CLEANER = Regex(
      """\n {6}- Other attributes?:[^\n]*(\n {10}[^\n]*)*"""
    )
    private val JVM_VERSION_ISSUE_PATTERN_6_0 = Regex("""Required org\.gradle\.jvm\.version '(\d+)' but no value provided\.""")
    private const val JVM_VERSION_ISSUE_JUNIT6_START = "Cannot choose between the following variants of org.junit.jupiter:junit-jupiter:6"
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

  init {
    addDescription(buildDescription(failureMessage))
    configureQuickFix(
      projectPath,
      failureMessage,
      isOfflineMode,
      GradleBundle.message("gradle.build.quick.fix.disable.offline.mode.reload", offlineQuickFixId)
    )
    if (isOfflineMode) addQuickFix(DisableOfflineAndReimport(projectPath))
  }

  class DisableOfflineAndReimport(private val projectPath: String) : BuildIssueQuickFix {
    override val id: String = offlineQuickFixId
    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      GradleSettings.getInstance(project).isOfflineWork = false
      return tryRerun(dataContext) ?: ExternalSystemUtil.requestImport(project, projectPath, GradleConstants.SYSTEM_ID)
    }
  }
}

@ApiStatus.Experimental
class UnresolvedDependencyBuildIssue(dependencyName: String,
                                     failureMessage: String?,
                                     isOfflineMode: Boolean) : UnresolvedDependencyIssue(dependencyName) {
  init {
    addDescription(buildDescription(failureMessage))
    configureQuickFix(
      null,
      failureMessage,
      isOfflineMode,
      GradleBundle.message("gradle.build.quick.fix.disable.offline.mode.rebuild", offlineQuickFixId)
    )
    if (isOfflineMode) addQuickFix(DisableOfflineAndRerun())
  }

  class DisableOfflineAndRerun : BuildIssueQuickFix {
    override val id: String = offlineQuickFixId
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