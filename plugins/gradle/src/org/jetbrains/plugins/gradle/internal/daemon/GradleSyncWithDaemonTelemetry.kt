// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleTelemetryFormat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.toNioPathOrNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.telemetry.GradleDaemonOpenTelemetryUtil
import java.nio.file.Path

class GradleSyncWithDaemonTelemetry : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    descriptor.title = GradleBundle.message("action.Gradle.SyncWithDaemonTelemetry.file.chooser.popup.title")
    descriptor.description = GradleBundle.message("action.Gradle.SyncWithDaemonTelemetry.file.chooser.popup.description")
    FileChooser.chooseFile(descriptor, project, null) { target ->
      val targetFolder = target.toNioPathOrNull() ?: return@chooseFile
      FileDocumentManager.getInstance().saveAllDocuments()
      GradleCoroutineScopeProvider.getInstance(project).cs
        .launch {
          if (ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProjectAsync(project, GradleConstants.SYSTEM_ID)) {
            readAction {
              ExternalSystemActionsCollector.trigger(project, GradleConstants.SYSTEM_ID, this@GradleSyncWithDaemonTelemetry, e)
            }
            val userData = getUserData(targetFolder)
            val spec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
              .withUserData(userData)
            ExternalSystemUtil.refreshProjects(spec)
          }
        }
    }
  }

  private fun getUserData(path: Path): UserDataHolderBase {
    return UserDataHolderBase().apply {
      putUserData(GradleDaemonOpenTelemetryUtil.DAEMON_TELEMETRY_ENABLED_KEY, true)
      putUserData(GradleDaemonOpenTelemetryUtil.DAEMON_TELEMETRY_FORMAT_KEY, GradleTelemetryFormat.JSON)
      putUserData(GradleDaemonOpenTelemetryUtil.DAEMON_TELEMETRY_TARGET_FOLDER_KEY, path)
    }
  }
}