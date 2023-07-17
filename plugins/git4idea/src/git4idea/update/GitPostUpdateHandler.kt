// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository

/**
 * Implement #GitPostUpdateHandler to execute code after git update has been finished
 */
interface GitPostUpdateHandler {
  companion object {
    val EP_NAME: ExtensionPointName<GitPostUpdateHandler> = ExtensionPointName.create("Git4Idea.gitPostUpdateHandler")

    fun execute(project: Project, ranges: Map<GitRepository, HashRange>, consumer: (PostUpdateData) -> Unit) {
      EP_NAME.extensionList.forEach { handler -> handler.execute(project, ranges, consumer) }
    }

    data class PostUpdateData(val text: String)

    @JvmStatic
    val POST_UPDATE_DATA = DataKey.create<PostUpdateData>("POST_UPDATE_DATA")
  }

  /**
   * #execute is performed after the update session has been finished
   */
  fun execute(project: Project, ranges: Map<GitRepository, HashRange>, consumer: (PostUpdateData) -> Unit)
}