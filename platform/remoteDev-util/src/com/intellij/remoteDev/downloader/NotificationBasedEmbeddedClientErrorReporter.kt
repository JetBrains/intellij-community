package com.intellij.remoteDev.downloader

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.testFramework.LightVirtualFile

internal class NotificationBasedEmbeddedClientErrorReporter(private val project: Project?) : EmbeddedClientErrorReporter {
  override fun startupFailed(exitCode: Int, output: List<String>) {
    val outputString = output.joinToString("")
    thisLogger().warn("Embedded client failed to start with exit code $exitCode:\n$outputString")
    val notification = Notification(
      "IDE-errors",
      RemoteDevUtilBundle.message("notification.title.failed.to.start.client"),
      RemoteDevUtilBundle.message("notification.content.process.finished.with.exit.code.0", exitCode),
      NotificationType.ERROR
    )
    if (project != null) {
      notification.addAction(
        NotificationAction.createSimple(RemoteDevUtilBundle.message("action.notification.view.output")) {
          FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", outputString), true)
        }
      )
    }
    notification.notify(project)
  }
}