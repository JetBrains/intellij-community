// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil

@ApiStatus.Internal
class DeprecatedGradleVersionIssue(gradleVersion: GradleVersion, projectPath: String) : BuildIssue {
  override val title: String = "Gradle ${gradleVersion.version} support can be dropped in the next release"
  override val description: String
  override val quickFixes = mutableListOf<BuildIssueQuickFix>()
  override fun getNavigatable(project: Project): Navigatable? = null

  init {
    val oldestRecommendedGradleVersion = GradleJvmSupportMatrix.getOldestRecommendedGradleVersionByIdea()
    require(gradleVersion < oldestRecommendedGradleVersion)
    val issueDescription = StringBuilder()

    val ideVersionName = ApplicationInfoImpl.getShadowInstance().versionName
    issueDescription.append("""
      The project uses Gradle ${gradleVersion.version}.
      The support for Gradle older that ${oldestRecommendedGradleVersion.version} will likely be dropped by $ideVersionName in the next release.
      
      Gradle ${oldestRecommendedGradleVersion.version} release notes can be found at https://docs.gradle.org/${oldestRecommendedGradleVersion.version}/release-notes.html
      """.trimIndent())

    issueDescription.append("\n\nPossible solution:\n")
    val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
    if (wrapperPropertiesFile == null) {
      val gradleVersionFix = GradleVersionQuickFix(projectPath, oldestRecommendedGradleVersion, true)
      issueDescription.append(
        " - <a href=\"${gradleVersionFix.id}\">Upgrade Gradle wrapper to ${oldestRecommendedGradleVersion.version} version " +
        "and re-import the project</a>\n")
      quickFixes.add(gradleVersionFix)
    }
    else {
      val wrapperSettingsOpenQuickFix = GradleWrapperSettingsOpenQuickFix(projectPath, "distributionUrl")
      val reimportQuickFix = ReimportQuickFix(projectPath, GradleConstants.SYSTEM_ID)
      issueDescription.append(" - <a href=\"${wrapperSettingsOpenQuickFix.id}\">Open Gradle wrapper settings</a>, " +
                              "change `distributionUrl` property to use Gradle ${oldestRecommendedGradleVersion.version} or newer and <a href=\"${reimportQuickFix.id}\">reload the project</a>\n")
      quickFixes.add(wrapperSettingsOpenQuickFix)
      quickFixes.add(reimportQuickFix)
    }

    description = issueDescription.toString()
  }
}