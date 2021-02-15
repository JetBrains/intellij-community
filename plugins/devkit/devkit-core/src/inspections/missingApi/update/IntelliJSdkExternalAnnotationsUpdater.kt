// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.update

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
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.IntelliJSdkExternalAnnotations
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.PublicIntelliJSdkExternalAnnotationsRepository
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.getAnnotationsBuildNumber
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.isAnnotationsRoot
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Utility class that updates external annotations of IntelliJ SDK.
 */
class IntelliJSdkExternalAnnotationsUpdater {

  companion object {
    private val LOG = Logger.getInstance(IntelliJSdkExternalAnnotationsUpdater::class.java)

    private val UPDATE_RETRY_TIMEOUT = Duration.of(1, ChronoUnit.HOURS)

    fun getInstance(): IntelliJSdkExternalAnnotationsUpdater = service()
  }

  private val buildNumberLastFailedUpdateInstant = hashMapOf<BuildNumber, Instant>()

  private val buildNumberUpdateInProgress = hashSetOf<BuildNumber>()

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

    if (synchronized(this) { buildNumber in buildNumberUpdateInProgress }) {
      return
    }

    val attachedAnnotations = getAttachedAnnotationsBuildNumbers(ideaJdk)
    if (attachedAnnotations.any { it >= buildNumber }) {
      return
    }

    runInEdt {
      synchronized(this) {
        val lastFailedInstant = buildNumberLastFailedUpdateInstant[buildNumber]
        val lastFailedWasLongAgo = lastFailedInstant == null || Instant.now().isAfter(lastFailedInstant.plus(UPDATE_RETRY_TIMEOUT))
        if (lastFailedWasLongAgo) {
          updateAnnotationsInBackground(ideaJdk, project, buildNumber)
        }
      }
    }
  }

  private fun reattachAnnotations(project: Project, ideaJdk: Sdk, annotationsRoot: IntelliJSdkExternalAnnotations) {
    val annotationsUrl = runReadAction { annotationsRoot.annotationsRoot.url }

    ApplicationManager.getApplication().invokeAndWait {
      if (project.isDisposed) {
        return@invokeAndWait
      }

      runWriteAction {
        val type = AnnotationOrderRootType.getInstance()
        val sdkModificator = ideaJdk.sdkModificator
        val attachedUrls = sdkModificator.getRoots(type)
          .asSequence()
          .filter { isAnnotationsRoot(it) }
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


  private fun updateAnnotationsInBackground(ideaJdk: Sdk, project: Project, buildNumber: BuildNumber) {
    if (synchronized(this) { !buildNumberUpdateInProgress.add(buildNumber) }) {
      return
    }

    UpdateTask(project, ideaJdk, buildNumber).queue()
  }

  private inner class UpdateTask(
    project: Project,
    private val ideaJdk: Sdk,
    private val ideBuildNumber: BuildNumber
  ) : Task.Backgroundable(project, DevKitBundle.message("intellij.api.annotations.update.task.title", ideBuildNumber), true) {

    private val ideAnnotations = AtomicReference<IntelliJSdkExternalAnnotations>()

    override fun run(indicator: ProgressIndicator) {
      val annotations = tryDownloadAnnotations(ideBuildNumber)
                        ?: throw Exception("No external annotations found for $ideBuildNumber in the Maven repository.")

      ideAnnotations.set(annotations)
      reattachAnnotations(project, ideaJdk, annotations)
    }

    private fun tryDownloadAnnotations(ideBuildNumber: BuildNumber): IntelliJSdkExternalAnnotations? {
      return try {
        PublicIntelliJSdkExternalAnnotationsRepository(project).downloadExternalAnnotations(ideBuildNumber)
      }
      catch (e: Exception) {
        throw Exception("Failed to download annotations for $ideBuildNumber", e)
      }
    }

    override fun onSuccess() {
      synchronized(this@IntelliJSdkExternalAnnotationsUpdater) {
        buildNumberLastFailedUpdateInstant.remove(ideBuildNumber)
      }
    }

    override fun onThrowable(error: Throwable) {
      synchronized(this@IntelliJSdkExternalAnnotationsUpdater) {
        buildNumberLastFailedUpdateInstant[ideBuildNumber] = Instant.now()
      }
      LOG.warn("Failed to update IntelliJ API external annotations", error)
    }

    override fun onFinished() {
      synchronized(this@IntelliJSdkExternalAnnotationsUpdater) {
        buildNumberUpdateInProgress.remove(ideBuildNumber)
      }
    }
  }

}