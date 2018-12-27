// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.update

import com.intellij.codeInspection.ex.modifyAndCommitProjectProfile
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.util.Consumer
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.PublicIdeExternalAnnotationsRepository
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Utility class that updates IntelliJ API external annotations.
 */
class IdeExternalAnnotationsUpdater(project: Project) {

  companion object {
    private val LOG = Logger.getInstance(IdeExternalAnnotationsUpdater::class.java)

    private const val NOTIFICATION_GROUP_DISPLAY_ID = "IntelliJ API Annotations"

    private val UPDATE_RETRY_TIMEOUT = Duration.of(10, ChronoUnit.MINUTES)

    private const val BUILD_TXT_FILE_NAME = "build.txt"

    private val ANNOTATIONS_BUILD_NUMBER_KEY = Key.create<BuildNumber>("devkit.intellij.api.annotations.build.number")

    private fun getAnnotationsBuildNumber(annotationsRoot: VirtualFile): BuildNumber? {
      val cachedValue = annotationsRoot.getUserData(ANNOTATIONS_BUILD_NUMBER_KEY)
      if (cachedValue != null) {
        return cachedValue
      }
      val loadedValue = loadBuildNumber(annotationsRoot)
      annotationsRoot.putUserData(ANNOTATIONS_BUILD_NUMBER_KEY, loadedValue)
      return loadedValue
    }

    private fun loadBuildNumber(annotationsRoot: VirtualFile): BuildNumber? {
      val buildTxtFile = annotationsRoot.findFileByRelativePath(BUILD_TXT_FILE_NAME)
      if (buildTxtFile != null) {
        return BuildNumber.fromStringOrNull(VfsUtil.loadText(buildTxtFile))
      }
      return null
    }

    private fun VirtualFile.isAnnotationsRoot() = getAnnotationsBuildNumber(this) != null

    fun getInstance(project: Project): IdeExternalAnnotationsUpdater = project.service()
  }

  private val buildNumberLastFailedUpdateInstant = hashMapOf<BuildNumber, Instant>()

  private val buildNumberNotificationShown = hashSetOf<BuildNumber>()

  private val buildNumberUpdateInProgress = hashSetOf<BuildNumber>()

  private val annotationsRepository = PublicIdeExternalAnnotationsRepository(project)

  private fun isInspectionEnabled(project: Project): Boolean {
    return runReadAction {
      ProjectInspectionProfileManager.getInstance(project).currentProfile
        .getTools(MissingRecentApiInspection.INSPECTION_SHORT_NAME, project)
        .isEnabled
    }
  }

  fun updateIdeaJdkAnnotationsIfNecessary(project: Project, ideaJdk: Sdk, buildNumber: BuildNumber) {
    if (!isInspectionEnabled(project)) {
      return
    }

    if (synchronized(this) { buildNumber in buildNumberUpdateInProgress || buildNumber in buildNumberNotificationShown }) {
      return
    }

    val attachedAnnotations = getAttachedAnnotationsBuildNumbers(ideaJdk)
    if (attachedAnnotations.any { it >= buildNumber }) {
      return
    }

    runInEdt {
      val canShowNotification = synchronized(this) {
        val lastFailedInstant = buildNumberLastFailedUpdateInstant[buildNumber]
        val lastFailedWasLongAgo = lastFailedInstant == null || Instant.now().isAfter(lastFailedInstant.plus(UPDATE_RETRY_TIMEOUT))
        if (lastFailedWasLongAgo && buildNumber !in buildNumberNotificationShown) {
          buildNumberNotificationShown.add(buildNumber)
          true
        } else {
          false
        }
      }

      if (canShowNotification) {
        showUpdateAnnotationsNotification(project, ideaJdk, buildNumber)
      }
    }
  }

  private fun reattachAnnotations(project: Project, ideaJdk: Sdk, annotationsRoot: VirtualFile) {
    val annotationsUrl = runReadAction { annotationsRoot.url }

    ApplicationManager.getApplication().invokeAndWait {
      if (project.isDisposed) {
        return@invokeAndWait
      }

      runWriteAction {
        val type = AnnotationOrderRootType.getInstance()
        val sdkModificator = ideaJdk.sdkModificator
        val attachedUrls = sdkModificator.getRoots(type)
          .asSequence()
          .filter { it.isAnnotationsRoot() }
          .mapTo(mutableSetOf()) { it.url }

        if (annotationsUrl !in attachedUrls) {
          sdkModificator.addRoot(annotationsUrl, type)
        }

        for (redundantUrl in attachedUrls - annotationsUrl) {
          sdkModificator.removeRoot(redundantUrl, type)
        }
        sdkModificator.commitChanges()
      }
    }
  }

  private fun getAttachedAnnotationsBuildNumbers(ideaJdk: Sdk): List<BuildNumber> =
    ideaJdk.rootProvider
      .getFiles(AnnotationOrderRootType.getInstance())
      .mapNotNull { getAnnotationsBuildNumber(it) }


  private fun showUpdateAnnotationsNotification(project: Project, ideaJdk: Sdk, buildNumber: BuildNumber) {
    val notificationGroup = NotificationGroup(NOTIFICATION_GROUP_DISPLAY_ID, NotificationDisplayType.STICKY_BALLOON, true)

    val notification = notificationGroup.createNotification(
      DevKitBundle.message("intellij.api.annotations.update.title", buildNumber),
      DevKitBundle.message("intellij.api.annotations.update.confirmation.content", buildNumber),
      NotificationType.INFORMATION,
      null
    ).apply {
      addAction(object : NotificationAction(DevKitBundle.message("intellij.api.annotations.update.confirmation.update.button")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          updateAnnotationsInBackground(ideaJdk, project, buildNumber)
          notification.expire()
        }
      })
      addAction(object : NotificationAction(
        DevKitBundle.message("intellij.api.annotations.update.confirmation.disable.inspection.button")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          disableInspection(project)
          notification.expire()
        }
      })
    }

    notification.whenExpired {
      synchronized(this) {
        buildNumberNotificationShown.remove(buildNumber)
      }
    }
    notification.notify(project)
  }

  private fun disableInspection(project: Project) {
    modifyAndCommitProjectProfile(project, Consumer {
      it.disableToolByDefault(listOf(MissingRecentApiInspection.INSPECTION_SHORT_NAME), project)
    })
  }

  private fun updateAnnotationsInBackground(ideaJdk: Sdk, project: Project, buildNumber: BuildNumber) {
    if (synchronized(this) { !buildNumberUpdateInProgress.add(buildNumber) }) {
      return
    }

    UpdateTask(project, ideaJdk, buildNumber).queue()
  }

  private fun showSuccessfullyUpdated(project: Project, buildNumber: BuildNumber) {
    synchronized(this) {
      buildNumberLastFailedUpdateInstant.remove(buildNumber)
    }
    NotificationGroup.balloonGroup(NOTIFICATION_GROUP_DISPLAY_ID)
      .createNotification(
        DevKitBundle.message("intellij.api.annotations.update.title", buildNumber),
        DevKitBundle.message("intellij.api.annotations.update.successfully.updated", buildNumber),
        NotificationType.INFORMATION,
        null
      )
      .notify(project)
  }

  private fun showUnableToUpdate(project: Project, throwable: Throwable, buildNumber: BuildNumber) {
    synchronized(this) {
      buildNumberLastFailedUpdateInstant[buildNumber] = Instant.now()
    }

    val message = DevKitBundle.message("intellij.api.annotations.update.failed", throwable.message)
    LOG.warn(message, throwable)

    NotificationGroup.balloonGroup(NOTIFICATION_GROUP_DISPLAY_ID)
      .createNotification(
        DevKitBundle.message("intellij.api.annotations.update.title", buildNumber),
        message,
        NotificationType.WARNING,
        null
      ).notify(project)
  }

  private inner class UpdateTask(
    project: Project,
    private val ideaJdk: Sdk,
    private val ideBuildNumber: BuildNumber
  ) : Task.Backgroundable(project, "Updating IntelliJ API Annotations $ideBuildNumber", true) {

    override fun run(indicator: ProgressIndicator) {
      val ideAnnotations = tryDownloadAnnotations(ideBuildNumber)
        ?: throw Exception("No external annotations found for $ideBuildNumber in the Maven repository")

      reattachAnnotations(project, ideaJdk, ideAnnotations)
    }

    private fun tryDownloadAnnotations(ideBuildNumber: BuildNumber): VirtualFile? {
      return try {
        annotationsRepository.downloadExternalAnnotations(ideBuildNumber)
      } catch (e: Exception) {
        throw Exception("Failed to download annotations for $ideBuildNumber", e)
      }
    }

    override fun onSuccess() {
      showSuccessfullyUpdated(project, ideBuildNumber)
    }

    override fun onThrowable(error: Throwable) {
      showUnableToUpdate(project, error, ideBuildNumber)
    }

    override fun onFinished() {
      synchronized(this@IdeExternalAnnotationsUpdater) {
        buildNumberUpdateInProgress.remove(ideBuildNumber)
      }
    }
  }

}