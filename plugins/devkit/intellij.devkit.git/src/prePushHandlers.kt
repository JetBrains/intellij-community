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
  override val commitMessageRegex = buildRegexFromAcceptableProjects()
  override val pathsToIgnore = super.pathsToIgnore.toMutableList()
    .apply { add("/fleet/plugins/kotlin/") }
    .apply { add("/plugins/kotlin/jupyter/") }

  override fun isAvailable(): Boolean = Registry.`is`("kotlin.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.kotlin.handler.name")
}

internal class KotlinNotebookPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/kotlin/jupyter/")
  override val acceptableProjects: List<String> = super.acceptableProjects + listOf(
    "PY"
  )
  override val commitMessageRegex = buildRegexFromAcceptableProjects()

  override fun isAvailable(): Boolean = Registry.`is`("kotlin.notebook.commit.message.validation.enabled", true)
  override fun getPresentableName(): String = DevKitGitBundle.message("push.commit.kotlin.notebook.handler.name")
}

internal class IntelliJPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("community", "platform")
  override val pathsToIgnore: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*[A-Z]+-\\d+.*", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("(tests|cleanup):.*")

  override fun isAvailable(): Boolean = Registry.`is`("intellij.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.handler.idea.name")
}

internal class IntelliJPlatformPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("/community/platform/")
  override val pathsToIgnore: List<String> = listOf()
  override val commitMessageRegex = Regex("""(?:^|.*[^-A-Z0-9])[A-Z]+-(?:[A-Z]+-)?\d+.*""", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("""^(?:\[.+\] ?)?\[?(?:tests?|cleanup|docs?|typo|refactor(?:ing)?|format|style|testFramework|test framework)\]?.*\s.*[A-Z0-9].*""", RegexOption.IGNORE_CASE)

  override fun isAvailable(): Boolean = Registry.`is`("intellij.platform.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.intellij.platform.handler.name")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean = true

  override fun doCommitsViolateRule(project: Project, commitsToWarnAbout: List<Pair<String, String>>, modalityState: ModalityState): Boolean {
    val commitsInfo = commitsToWarnAbout.joinToString("<br/>") { (hash, subject) ->
      "$hash: $subject"
    }

    val commitAsIs = invokeAndWait(modalityState) {
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("push.commit.intellij.platform.handler.title"),
        DevKitGitBundle.message("push.commit.intellij.platform.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project)
    }

    return !commitAsIs
  }
}

internal class KotlinBuildToolsPrePushHandler : AbstractIntelliJProjectPrePushHandler() {

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

internal class AiAssistantPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/llm/", "plugins/llm-installer/", "plugins/full-line/")
  override val commitMessageRegex = Regex(".*\\b[A-Z]{2,}-\\d+\\b.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)
  private val protectedBranches = listOf("ij-ai/", "ij-aia/", "ide-next/")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean {
    return super.isTargetBranchProtected(project, pushInfo) || protectedBranches.any { pushInfo.pushSpec.target.presentation.startsWith(it) }
  }

  override fun isAvailable(): Boolean = Registry.`is`("aia.commit.message.validation.enabled", true)
  override fun doCommitsViolateRule(project: Project, commitsToWarnAbout: List<Pair<String, String>>, modalityState: ModalityState): Boolean {

    val commitsInfo = commitsToWarnAbout.joinToString("<br/>") { hashAndSubject ->
      "${hashAndSubject.first}: ${hashAndSubject.second}"
    }

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("aia.push.commit.message.lacks.issue.reference.title"),
        DevKitGitBundle.message("aia.push.commit.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project = null)
    }

    return !commitAsIs

  }

  override fun getPresentableName(): String = DevKitGitBundle.message("aia.commit.handler.name")
}