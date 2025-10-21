// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls
import java.nio.file.Path

internal class KotlinNotebookPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/kotlin/jupyter/")
  override val acceptableProjects: List<String> = super.acceptableProjects + listOf(
    "PY"
  )
  override val commitMessageRegex = buildRegexFromAcceptableProjects()

  override fun isAvailable(): Boolean = Registry.`is`("kotlin.notebook.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.kotlin.notebook.handler.name")
}


private val KOTLIN_BUILD_TOOLS_PATHS_TO_IGNORE: List<String> =
  listOf("community/plugins/kotlin/gradle/gradle-java/k1/test/org/jetbrains/kotlin/idea/scripting/")

// To be in sync with https://jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/.teamcity/src/idea/cherryPickRobot/branchReviewRules/BranchReviewRules.kt
// see approval(ReviewerGroups.kotlinBuildToolsTeam)
private val KOTLIN_BUILD_TOOLS_PATHS: List<String> =
  listOf(
    "community/plugins/kotlin/base/facet/",
    "community/plugins/kotlin/base/jps/",
    "community/plugins/kotlin/base/external-build-system/",
    "community/plugins/kotlin/gradle/gradle/",
    "community/plugins/kotlin/gradle/gradle-tooling/",
    "community/plugins/kotlin/gradle/gradle-java/",
    "community/plugins/kotlin/gradle/multiplatform-tests/",
    "community/plugins/kotlin/gradle/multiplatform-tests-k2/",
    "community/plugins/kotlin/jps/",
    "community/plugins/kotlin/maven/"
  )

internal class KotlinBuildToolsPrePushHandler : AbstractIntelliJProjectPrePushHandler() {
  override fun isAvailable(): Boolean =
    Registry.`is`("kotlin.build.tools.code.ownership.commit.message.enabled", true)

  override fun getPresentableName(): @Nls(capitalization = Nls.Capitalization.Title) String =
    DevKitGitBundle.message("push.commit.kotlin.build.tools.handler.name")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean {
    return super.isTargetBranchProtected(project, pushInfo) || pushInfo.pushSpec.target.presentation == "kt-master"
  }

  override fun validate(project: Project, info: PushInfo, indicator: ProgressIndicator): PushInfoValidationResult {
    val matchingCommits = info.commits.filter { commit ->
      commit.changes.asSequence()
        .mapNotNull { it.virtualFile }
        .map { Path.of(it.path) }
        .anyIn(KOTLIN_BUILD_TOOLS_PATHS, KOTLIN_BUILD_TOOLS_PATHS_TO_IGNORE)
    }

    if (matchingCommits.isNotEmpty()) {
      val commitsInfo = matchingCommits.toHtml()
      invokeAndWait(indicator.modalityState) {
        MessageDialogBuilder.okCancel(
          DevKitGitBundle.message("push.commit.kotlin.build.tools.review.title"),
          DevKitGitBundle.message("push.commit.kotlin.build.tools.message.lacks.issue.reference.body", commitsInfo)
        )
          .asWarning()
          .ask(project = null)
      }
    }

    return PushInfoValidationResult.VALID
  }
}
