// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.upload

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.util.localizedMessageOrClassName
import org.jetbrains.plugins.gitlab.notification.GitLabNotificationIds.GL_NOTIFICATION_UPLOAD_FILE_ERROR
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import java.awt.Image
import java.nio.file.Path
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException


internal object GitLabUploadFileUtil {

  suspend fun uploadFileAndNotify(
    project: Project,
    projectData: GitLabProject,
    path: Path?,
  ): String? {
    val resultingPath = path ?: showDialog(project) ?: return null

    return withContext(Dispatchers.IO) {
      uploadFileAndNotify(project) { projectData.uploadFile(resultingPath) }
    }
  }

  suspend fun uploadImageAndNotify(
    project: Project,
    projectData: GitLabProject,
    image: Image,
  ): String? {
    return withContext(Dispatchers.IO) {
      uploadFileAndNotify(project) { projectData.uploadImage(ImageUtil.toBufferedImage(image)) }
    }
  }

  private suspend fun uploadFileAndNotify(
    project: Project,
    uploader: suspend () -> String,
  ): String? {
    try {
      return withBackgroundProgress(project, message("upload.file.action.progress"), true) {
        uploader()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: NoSuchFileException) {
      VcsNotifier.getInstance(project).notifyError(GL_NOTIFICATION_UPLOAD_FILE_ERROR, message("upload.file.action.error.title"),
                                                   message("upload.file.dialog.file.not.found"))
    }
    catch (_: AccessDeniedException) {
      VcsNotifier.getInstance(project).notifyError(GL_NOTIFICATION_UPLOAD_FILE_ERROR, message("upload.file.action.error.title"),
                                                   message("upload.file.dialog.not.readable"))
    }
    catch (e: Exception) {
      VcsNotifier.getInstance(project).notifyError(GL_NOTIFICATION_UPLOAD_FILE_ERROR, message("upload.file.action.error.title"),
                                                   e.localizedMessageOrClassName())
    }
    return null
  }

  private suspend fun showDialog(project: Project): Path? {
    return withContext(Dispatchers.Main) {
      FileChooser.chooseFile(FileChooserDescriptorFactory.singleFile().withTitle(message("upload.file.dialog.title")), project, null)?.toNioPath()
    }
  }
}