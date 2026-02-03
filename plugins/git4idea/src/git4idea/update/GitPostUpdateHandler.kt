// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.notification.NotificationAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository

/**
 * Implement #GitPostUpdateHandler to provide custom actions for the post update notification
 */
interface GitPostUpdateHandler {
  companion object {
    val EP_NAME: ExtensionPointName<GitPostUpdateHandler> = ExtensionPointName.create("Git4Idea.gitPostUpdateHandler")

    fun getActions(project: Project, ranges: Map<GitRepository, HashRange>): List<NotificationAction> {
      return EP_NAME.extensionList.flatMap { it.getActions(project, ranges) }
    }
  }

  /**
   * #execute is performed after the update session has been finished
   */
  fun getActions(project: Project, ranges: Map<GitRepository, HashRange>) : List<NotificationAction>
}