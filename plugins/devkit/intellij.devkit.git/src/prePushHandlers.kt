// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls

internal class KotlinPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*(?:KTIJ|KT|IDEA|IJPL)-\\d+.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)
  override val pathsToIgnore = super.pathsToIgnore.toMutableList()
    .apply { add("/fleet/plugins/kotlin/") }
    .apply { add("/plugins/kotlin/jupyter/") }

  override fun isAvailable(): Boolean = Registry.`is`("kotlin.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.handler.name")
}

internal class IntelliJPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("community", "platform")
  override val pathsToIgnore: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*[A-Z]+-\\d+.*", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("(tests|cleanup):.*")

  override fun isAvailable(): Boolean = Registry.`is`("intellij.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.handler.idea.name")
}

internal class KotlinBuildToolsPrePushHandler: AbstractIntelliJProjectPrePushHandler() {

  override val pathsToIgnore: List<String> =
    listOf("community/plugins/kotlin/gradle/gradle-java/k1/test/org/jetbrains/kotlin/idea/scripting/")

  // To be in sync with https://jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/.teamcity/src/idea/cherryPickRobot/branchReviewRules/BranchReviewRules.kt
  // see approval(ReviewerGroups.kotlinBuildToolsTeam)
  override val paths: List<String> =
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

  override fun isAvailable(): Boolean =
    Registry.`is`("kotlin.build.tools.code.ownership.commit.message.enabled", true)

  override fun getPresentableName(): @Nls(capitalization = Nls.Capitalization.Title) String =
    DevKitGitBundle.message("push.commit.kotlin.build.tools.handler.name")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean {
    return super.isTargetBranchProtected(project, pushInfo) || pushInfo.pushSpec.target.presentation == "kt-master"
  }

  override fun doCommitsViolateRule(project: Project, commitsToWarnAbout: List<Pair<String, String>>, modalityState: ModalityState): Boolean {
    val commitsInfo =
      commitsToWarnAbout
        .toList().joinToString("<br/>") { hashAndSubject ->
          "${hashAndSubject.first}: ${hashAndSubject.second}"
        }

    invokeAndWait(modalityState) {
      MessageDialogBuilder.okCancel(
        DevKitGitBundle.message("push.commit.kotlin.build.tools.review.title"),
        DevKitGitBundle.message("push.commit.kotlin.build.tools.message.lacks.issue.reference.body", commitsInfo)
      )
        .asWarning()
        .ask(project = null)
    }

    return true
  }
}