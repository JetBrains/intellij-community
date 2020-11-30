// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal
@file:JvmName("GradleJvmValidationUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.EditorHelper
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.GradleNotification.NOTIFICATION_GROUP
import org.jetbrains.plugins.gradle.util.GradleProperties.GradleProperty
import java.io.File
import java.nio.file.Path
import javax.swing.event.HyperlinkEvent

fun validateJavaHome(project: Project, externalProjectPath: Path, gradleVersion: GradleVersion) {
  val gradleProperties = getGradleProperties(externalProjectPath)
  val javaHomeProperty = gradleProperties.javaHomeProperty
  if (javaHomeProperty != null) {
    val javaHome = javaHomeProperty.value
    when (val validationStatus = validateGradleJavaHome(gradleVersion, javaHome)) {
      JavaHomeValidationStatus.Invalid -> notifyInvalidGradleJavaHomeInfo(project, javaHomeProperty, validationStatus)
      is JavaHomeValidationStatus.Unsupported -> notifyInvalidGradleJavaHomeInfo(project, javaHomeProperty, validationStatus)
    }
  }
  else {
    val javaHome = ExternalSystemJdkUtil.getJavaHome()
    when (val validationStatus = validateGradleJavaHome(gradleVersion, javaHome)) {
      JavaHomeValidationStatus.Invalid -> notifyInvalidJavaHomeInfo(project, validationStatus)
      is JavaHomeValidationStatus.Unsupported -> notifyInvalidJavaHomeInfo(project, validationStatus)
    }
  }
}

fun validateGradleJavaHome(gradleVersion: GradleVersion, javaHome: String?): JavaHomeValidationStatus {
  if (javaHome == null) return JavaHomeValidationStatus.Undefined
  if (!ExternalSystemJdkUtil.isValidJdk(javaHome)) return JavaHomeValidationStatus.Invalid
  val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
  val versionString = javaSdkType.getVersionString(javaHome) ?: return JavaHomeValidationStatus.Invalid
  val javaVersion = JavaVersion.tryParse(versionString) ?: return JavaHomeValidationStatus.Invalid
  if (!isSupported(gradleVersion, versionString)) return JavaHomeValidationStatus.Unsupported(javaVersion, gradleVersion)
  return JavaHomeValidationStatus.Success(javaHome)
}

/**
 * @see org.jetbrains.plugins.gradle.util.suggestGradleVersion
 */
fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  val baseVersion = gradleVersion.baseVersion
  return when {
    baseVersion >= GradleVersion.version("6.7") -> javaVersion.feature >= 8 //..15 https://docs.gradle.org/6.7/release-notes.html#java-15
    baseVersion >= GradleVersion.version("6.3") -> javaVersion.feature in 8..14 // many builds might work with Java 15 but there are some known issues https://github.com/gradle/gradle/issues/13532
    baseVersion >= GradleVersion.version("6.0") -> javaVersion.feature in 8..13
    baseVersion >= GradleVersion.version("5.4.1") -> javaVersion.feature in 8..12
    baseVersion >= GradleVersion.version("5.0") -> javaVersion.feature in 8..11
    baseVersion >= GradleVersion.version("4.1") -> javaVersion.feature in 7..9
    baseVersion >= GradleVersion.version("4.0") -> javaVersion.feature in 7..8
    else -> javaVersion.feature in 7..8
  }
}

fun isSupported(gradleVersion: GradleVersion, javaVersionString: String): Boolean {
  val javaVersion = JavaVersion.tryParse(javaVersionString) ?: return false
  return isSupported(gradleVersion, javaVersion)
}

fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion {
  val baseVersion = gradleVersion.baseVersion
  return when {
    baseVersion >= GradleVersion.version("6.7") -> JavaVersion.compose(15)
    baseVersion >= GradleVersion.version("6.3") -> JavaVersion.compose(14)
    baseVersion >= GradleVersion.version("6.0") -> JavaVersion.compose(13)
    baseVersion >= GradleVersion.version("5.4.1") -> JavaVersion.compose(12)
    baseVersion >= GradleVersion.version("5.0") -> JavaVersion.compose(11)
    baseVersion >= GradleVersion.version("4.1") -> JavaVersion.compose(9)
    else -> JavaVersion.compose(8)
  }
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion {
  val baseVersion = gradleVersion.baseVersion
  return when {
    baseVersion >= GradleVersion.version("5.0") -> JavaVersion.compose(8)
    else -> JavaVersion.compose(7)
  }
}

private fun notifyInvalidGradleJavaHomeInfo(
  project: Project,
  javaHomeProperty: GradleProperty<String>,
  reason: JavaHomeValidationStatus
) {
  val propertyLocation = createLinkToFile(project, javaHomeProperty.location)
  val notificationContent = GradleBundle.message("gradle.notifications.java.home.property.content", propertyLocation)
  notifyInvalidGradleJvmInfo(project, notificationContent, reason)
}

private fun notifyInvalidJavaHomeInfo(project: Project, reason: JavaHomeValidationStatus) {
  val notificationContent = GradleBundle.message("gradle.notifications.java.home.variable.content")
  notifyInvalidGradleJvmInfo(project, notificationContent, reason)
}

private fun createLinkToFile(project: Project, path: String): String {
  val projectDirectory = project.guessProjectDir()
  val projectPath = projectDirectory?.path?.let { toSystemDependentName(it) }
  val filePath = toSystemDependentName(path)
  val presentablePath = when {
    projectPath == null -> getLocationRelativeToUserHome(filePath)
    isAncestor(projectPath, filePath, true) -> getRelativePath(projectPath, filePath, File.separatorChar)
    else -> getLocationRelativeToUserHome(filePath)
  }
  return "<a href='$path'>$presentablePath</a>"
}

private fun notifyInvalidGradleJvmInfo(project: Project, @NlsContexts.HintText notificationHint: String, reason: JavaHomeValidationStatus) {
  val notificationTitle = GradleBundle.message("gradle.notifications.java.home.invalid.title")
  var notificationContent = notificationHint
  if (reason is JavaHomeValidationStatus.Unsupported) {
    val javaVersion = reason.javaVersion.toString()
    val gradleVersion = reason.gradleVersion.version
    val additionalFailureHint = GradleBundle.message("gradle.notifications.java.home.unsupported.content", javaVersion, gradleVersion)
    notificationContent = "$additionalFailureHint $notificationContent"
  }
  val hyperLinkProcessor = object : NotificationListener.Adapter() {
    override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
      val file = LocalFileSystem.getInstance().findFileByPath(e.description) ?: return
      ProjectViewSelectInTarget.select(project, file, ProjectViewPane.ID, null, file, true)
      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
      EditorHelper.openInEditor(psiFile)
    }
  }
  val notification = NOTIFICATION_GROUP.createNotification(notificationTitle, notificationContent, INFORMATION, hyperLinkProcessor)
  notification.notify(project)
}

sealed class JavaHomeValidationStatus {
  object Invalid : JavaHomeValidationStatus()
  object Undefined : JavaHomeValidationStatus()
  class Unsupported(val javaVersion: JavaVersion, val gradleVersion: GradleVersion) : JavaHomeValidationStatus()
  class Success(val javaHome: String) : JavaHomeValidationStatus()
}