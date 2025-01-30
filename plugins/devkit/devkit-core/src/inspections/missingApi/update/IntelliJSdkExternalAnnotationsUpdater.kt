// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.update

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import kotlinx.coroutines.*
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.missingApi.MISSING_API_INSPECTION_SHORT_NAME
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.IntelliJSdkExternalAnnotations
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.PublicIntelliJSdkExternalAnnotationsRepository
import org.jetbrains.idea.devkit.inspections.missingApi.resolve.getAnnotationsBuildNumber
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class that updates external annotations of IntelliJ SDK.
 */
@Service
internal class IntelliJSdkExternalAnnotationsUpdater(private val cs: CoroutineScope) {
  companion object {
    private val LOG = Logger.getInstance(IntelliJSdkExternalAnnotationsUpdater::class.java)

    private val UPDATE_RETRY_TIMEOUT = Duration.of(1, ChronoUnit.HOURS)
    private val PENDING = Instant.MAX

    fun getInstance(): IntelliJSdkExternalAnnotationsUpdater = service()
  }

  private val buildNumberLastFailedUpdateInstant = ConcurrentHashMap<BuildNumber, Instant>()

  fun updateIdeaJdkAnnotationsIfNecessary(project: Project, ideaJdk: Sdk) {
    cs.launch {
      val buildNumber = getIdeaBuildNumber(ideaJdk)
      if (buildNumber != null) {
        withContext(Dispatchers.EDT) { } // wait for non-modal (Apply button in Settings)
        updateIdeaJdkAnnotationsIfNecessaryInner(project, ideaJdk, buildNumber)
      }
    }
  }

  private suspend fun getIdeaBuildNumber(ideaJdk: Sdk): BuildNumber? {
    val homePath = ideaJdk.homePath ?: return null
    val buildNumberStr = withContext(Dispatchers.IO) {
      IdeaJdk.getBuildNumber(homePath)
    }
    if (buildNumberStr == null) return null
    return BuildNumber.fromStringOrNull(buildNumberStr)
  }

  private suspend fun updateIdeaJdkAnnotationsIfNecessaryInner(project: Project, ideaJdk: Sdk, buildNumber: BuildNumber) {
    val inspectionsEnabled = readAction {
      ProjectInspectionProfileManager.getInstance(project).currentProfile
        .getTools(MISSING_API_INSPECTION_SHORT_NAME, project)
        .isEnabled
    }
    if (!inspectionsEnabled) {
      return
    }

    val lastFailedInstant = buildNumberLastFailedUpdateInstant[buildNumber]
    if (lastFailedInstant === PENDING ||
        lastFailedInstant != null && !Instant.now().isAfter(lastFailedInstant.plus(UPDATE_RETRY_TIMEOUT))) {
      return
    }

    val roots = readAction { ideaJdk.rootProvider.getFiles(AnnotationOrderRootType.getInstance()) }
    val attachedAnnotations = roots.mapNotNull { getAnnotationsBuildNumber(it) }
    if (attachedAnnotations.any { it >= buildNumber }) {
      return
    }

    if (lastFailedInstant == null && buildNumberLastFailedUpdateInstant.putIfAbsent(buildNumber, PENDING) != null ||
        lastFailedInstant != null && !buildNumberLastFailedUpdateInstant.replace(buildNumber, lastFailedInstant, PENDING)) {
      return
    }
    try {
      withBackgroundProgress(project, DevKitBundle.message("intellij.api.annotations.update.task.title", buildNumber)) {
        val annotations = try {
          PublicIntelliJSdkExternalAnnotationsRepository(project).downloadExternalAnnotations(buildNumber)
        }
        catch (ex: CancellationException) {
          throw ex
        }
        catch (ex: Exception) {
          throw Exception("Failed to download annotations for $buildNumber", ex)
        }
        if (annotations == null) {
          throw Exception("No external annotations found for $buildNumber in the Maven repository.")
        }
        reattachAnnotations(ideaJdk, annotations)
      }
      buildNumberLastFailedUpdateInstant.remove(buildNumber)
    }
    catch (ex: CancellationException) {
      buildNumberLastFailedUpdateInstant.remove(buildNumber)
      throw ex
    }
    catch (ex: Throwable) {
      buildNumberLastFailedUpdateInstant[buildNumber] = Instant.now()
      LOG.warn("Failed to update IntelliJ API external annotations", ex)
    }
  }

  private suspend fun reattachAnnotations(ideaJdk: Sdk, annotationsRoot: IntelliJSdkExternalAnnotations) {
    val rootType = AnnotationOrderRootType.getInstance()
    val annotationsUrl = readAction { annotationsRoot.annotationsRoot.url }
    val roots = readAction { ideaJdk.rootProvider.getFiles(rootType) }
    val attachedUrls = roots.mapNotNull { if (getAnnotationsBuildNumber(it) != null) it.url else null }

    writeAction {
      val sdkModificator = ideaJdk.sdkModificator
      if (annotationsUrl !in attachedUrls) {
        sdkModificator.addRoot(annotationsUrl, rootType)
      }

      for (redundantUrl in attachedUrls - annotationsUrl) {
        sdkModificator.removeRoot(redundantUrl, rootType)
      }
      sdkModificator.commitChanges()
    }
  }
}