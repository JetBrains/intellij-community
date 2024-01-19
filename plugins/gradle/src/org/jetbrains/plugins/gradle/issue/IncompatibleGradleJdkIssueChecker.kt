// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.*
import java.io.File
import java.util.function.Consumer

/**
 * This issue checker provides quick fixes for known compatibility issues with Gradle and Java:
 * 1. Gradle versions less than 4.7 do not support JEP-322 (Java starting with JDK 10-ea build 36), see https://github.com/gradle/gradle/issues/4503
 * 2. Gradle versions less than 4.8 fails on JDK 11+ (due to dependency on Unsafe::defineClass which is removed in JDK 11), see https://github.com/gradle/gradle/issues/4860
 * 3. Gradle versions less than 4.7 can not be used by the IDE running on Java 9+, see https://github.com/gradle/gradle/issues/8431, https://github.com/gradle/gradle/issues/3355
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class IncompatibleGradleJdkIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val rootCauseName = rootCause.javaClass.simpleName
    val rootCauseText = rootCause.toString()
    val rootCauseMessage = rootCause.message ?: rootCauseName
    var gradleVersionUsed: GradleVersion? = null
    var buildEnvironmentJavaHome: File? = null
    if (issueData.buildEnvironment != null) {
      gradleVersionUsed = GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
      buildEnvironmentJavaHome = issueData.buildEnvironment.java.javaHome
    }

    val couldNotDetermineJavaIssue = couldNotDetermineJavaIssue(rootCause, rootCauseText)
    val isUnsupportedJavaRuntimeIssue = rootCauseName == UnsupportedJavaRuntimeException::class.java.simpleName ||
                                        couldNotDetermineJavaIssue
    val javaVersionUsed = detectJavaVersion(couldNotDetermineJavaIssue, rootCauseText, issueData, buildEnvironmentJavaHome)
    val isRemovedUnsafeDefineClassMethodInJDK11Issue = causedByUnsafeDefineClassApiUsage(gradleVersionUsed, javaVersionUsed,
                                                                                         rootCause, rootCauseText)
    val isUnsupportedClassVersionErrorIssue = rootCauseName == UnsupportedClassVersionError::class.java.simpleName &&
                                              javaVersionUsed != null && javaVersionUsed.feature < 7
    var unableToStartDaemonProcessForJDK9 = false
    var unableToStartDaemonProcessForJDK11 = false
    if (!isRemovedUnsafeDefineClassMethodInJDK11Issue &&
        rootCauseText.startsWith("org.gradle.api.GradleException: Unable to start the daemon process.") &&
        rootCauseText.contains("FAILURE: Build failed with an exception.") &&
        gradleVersionUsed != null && GradleVersionUtil.isGradleOlderOrSameAs(gradleVersionUsed, "4.6")) {
      if (GradleVersionUtil.isGradleOlderThan(gradleVersionUsed, "3.0")) {
        unableToStartDaemonProcessForJDK9 = true
      }
      else {
        unableToStartDaemonProcessForJDK11 = true
      }
    }

    val isUnsupportedJavaVersionForGradle =
      javaVersionUsed != null &&
      gradleVersionUsed != null &&
      !GradleJvmSupportMatrix.isSupported(gradleVersionUsed, javaVersionUsed)

    if (!isUnsupportedJavaVersionForGradle &&
        !isUnsupportedClassVersionErrorIssue &&
        !isUnsupportedJavaRuntimeIssue &&
        !isRemovedUnsafeDefineClassMethodInJDK11Issue &&
        !unableToStartDaemonProcessForJDK11 &&
        !unableToStartDaemonProcessForJDK9) {
      return null
    }

    val oldestCompatibleJavaVersion = gradleVersionUsed?.let { GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(it) }
                                      ?: GradleJvmSupportMatrix.getOldestRecommendedJavaVersionByIdea()
    val newestCompatibleJavaVersion = gradleVersionUsed?.let { GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(it) }
                                      ?: GradleJvmSupportMatrix.getOldestRecommendedJavaVersionByIdea()
    val suggestedJavaVersion = when {
      javaVersionUsed == null -> newestCompatibleJavaVersion
      javaVersionUsed < oldestCompatibleJavaVersion -> oldestCompatibleJavaVersion
      javaVersionUsed > newestCompatibleJavaVersion -> newestCompatibleJavaVersion
      else -> newestCompatibleJavaVersion
    }

    val oldestCompatibleGradleVersion = javaVersionUsed?.let { GradleJvmSupportMatrix.suggestOldestSupportedGradleVersion(it) }
                                        ?: GradleJvmSupportMatrix.getOldestRecommendedGradleVersionByIdea()
    val newestCompatibleGradleVersion = javaVersionUsed?.let { GradleJvmSupportMatrix.suggestLatestSupportedGradleVersion(it) }
    val suggestedGradleVersion = when {
      newestCompatibleGradleVersion == null -> oldestCompatibleGradleVersion.version + " or newer"
      oldestCompatibleGradleVersion == newestCompatibleGradleVersion -> oldestCompatibleGradleVersion.version
      else -> "version from range [${oldestCompatibleGradleVersion.version}, ${newestCompatibleGradleVersion.version}]"
    }

    val gradleVersionString = gradleVersionUsed?.version ?: "version"
    val javaVersionString = if (javaVersionUsed != null) "Java $javaVersionUsed" else "incompatible Java"

    return object : AbstractGradleBuildIssue() {
      init {
        when {
          isRemovedUnsafeDefineClassMethodInJDK11Issue -> {
            setTitle("Unable to start the daemon process")
            addDescription("Unable to start the daemon process.")
            addDescription(
              "The project uses Gradle $gradleVersionString which is incompatible with Java 11 or newer.\n" +
              "See details at https://github.com/gradle/gradle/issues/4860"
            )
          }
          unableToStartDaemonProcessForJDK9 -> {
            setTitle("Unable to start the daemon process")
            addDescription("Unable to start the daemon process.")
            addDescription("The project uses Gradle $gradleVersionString which is incompatible with Java 9 or newer.")
          }
          unableToStartDaemonProcessForJDK11 -> {
            setTitle("Unable to start the daemon process")
            addDescription("Unable to start the daemon process.")
            addDescription("The project uses Gradle $gradleVersionString which is incompatible with Java 11 or newer.")
          }
          couldNotDetermineJavaIssue -> {
            setTitle("Unsupported Java")
            addDescription("Unsupported Java.")
            addDescription(
              "Your build is currently configured to use $javaVersionString. It's recommended to use Gradle $suggestedGradleVersion."
            )
          }
          isUnsupportedClassVersionErrorIssue -> {
            setTitle("Unsupported Java")
            addDescription("Unsupported Java.")
            addDescription("Your build is currently configured to use $javaVersionString. You need to use at least Java 7.")
          }
          isUnsupportedJavaVersionForGradle -> {
            setTitle("Unsupported Java")
            addDescription("Unsupported Java.")
            addDescription("Your build is currently configured to use Java $javaVersionUsed and Gradle $gradleVersionString.")
          }
          else -> {
            addDescription(rootCauseMessage)
          }
        }

        val isAndroidStudio = "AndroidStudio" == PlatformUtils.getPlatformPrefix()
        if (!isAndroidStudio) { // Android Studio doesn't have Gradle JVM setting
          val gradleSettingsFix = GradleSettingsQuickFix(
            issueData.projectPath, true,
            GradleSettingsQuickFix.GradleJvmChangeDetector,
            GradleBundle.message("gradle.settings.text.jvm.path")
          )
          addQuickFixPrompt("Use Java $suggestedJavaVersion as Gradle JVM: <a href=\"${gradleSettingsFix.id}\">Open Gradle settings</a>")
          addQuickFix(gradleSettingsFix)
        }

        if (!isUnsupportedClassVersionErrorIssue) {
          val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(issueData.projectPath)
          if (wrapperPropertiesFile == null || gradleVersionUsed != null && gradleVersionUsed.baseVersion < oldestCompatibleGradleVersion) {
            val gradleVersionFix = GradleVersionQuickFix(issueData.projectPath, oldestCompatibleGradleVersion, true)
            addQuickFixPrompt(
              "<a href=\"${gradleVersionFix.id}\">Upgrade Gradle wrapper to ${oldestCompatibleGradleVersion.version} version " +
              "and re-import the project</a>"
            )
            addQuickFix(gradleVersionFix)
          }
          else {
            val wrapperSettingsOpenQuickFix = GradleWrapperSettingsOpenQuickFix(issueData.projectPath, "distributionUrl")
            val reimportQuickFix = ReimportQuickFix(issueData.projectPath, GradleConstants.SYSTEM_ID)
            addQuickFixPrompt(
              "<a href=\"${wrapperSettingsOpenQuickFix.id}\">Open Gradle wrapper settings</a>, " +
              "change `distributionUrl` property to use compatible Gradle version and <a href=\"${reimportQuickFix.id}\">reload the project</a>"
            )
            addQuickFix(wrapperSettingsOpenQuickFix)
            addQuickFix(reimportQuickFix)
          }
        }
      }
    }
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    // JDK compatibility issues should be handled by IncompatibleGradleJdkIssueChecker.check method based on exceptions come from Gradle TAPI
    return failureCause.startsWith("Could not create service of type ") && failureCause.contains(" using BuildScopeServices.")
  }

  companion object {
    private const val couldNotDetermineJavaUsingExecutablePrefix = "org.gradle.api.GradleException: Could not determine Java version using executable "
    private fun couldNotDetermineJavaIssue(rootCause: Throwable, rootCauseText: String) =
      rootCauseText.startsWith(couldNotDetermineJavaUsingExecutablePrefix) ||
      rootCause.message == "Could not determine Java version."

    // https://github.com/gradle/gradle/issues/4860
    private fun causedByUnsafeDefineClassApiUsage(gradleVersionUsed: GradleVersion?,
                                                  javaVersion: JavaVersion?,
                                                  rootCause: Throwable,
                                                  rootCauseText: String): Boolean {
      val baseVersion = gradleVersionUsed?.baseVersion ?: return false
      if (baseVersion > GradleVersion.version("4.7")) return false
      if (rootCauseText.startsWith("java.lang.NoSuchMethodError: sun.misc.Unsafe.defineClass") &&
          javaVersion?.feature?.let { it >= 11 } == true) return true

      val message = rootCause.message ?: return false
      if (!message.startsWith("'java.lang.Class sun.misc.Unsafe.defineClass")) return false
      return rootCause.stackTrace.find { it.className == "org.gradle.internal.classloader.ClassLoaderUtils" } != null
    }

    private fun detectJavaVersion(couldNotDetermineJavaIssue: Boolean,
                                  rootCauseText: String,
                                  issueData: GradleIssueData,
                                  javaHome: File?) = if (couldNotDetermineJavaIssue) {
      val javaExeCandidate = rootCauseText.substringAfter(couldNotDetermineJavaUsingExecutablePrefix).trimEnd('.')
      val javaHomeCandidate = File(javaExeCandidate).parentFile?.parentFile ?: javaHome
      javaHomeCandidate?.let {
        if (it.isDirectory) JdkVersionDetector.getInstance().detectJdkVersionInfo(it.path)?.version else null
      }
    }
    else {
      issueData.buildEnvironment?.java?.javaHome?.let {
        return@let JdkVersionDetector.getInstance().detectJdkVersionInfo(it.path)?.version
      }
    }
  }
}
