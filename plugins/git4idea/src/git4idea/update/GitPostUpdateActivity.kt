// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsUser

/**
 * Implement #GitPostUpdateHandler to execute code after git update has been finished
 */
interface GitPostUpdateHandler {
  companion object {
    val EP_NAME: ExtensionPointName<GitPostUpdateHandler> = ExtensionPointName.create("Git4Idea.gitPostUpdateHandler")

    fun execute(project: Project, fetches: Map<VcsUser, List<String>>, consumer: (String) -> Unit) {
      EP_NAME.extensionList.forEach { handler -> handler.execute(project, fetches, consumer) }
    }
  }

  /**
   * #execute is performed after the update session has been finished
   */
  fun execute(project: Project, fetches: Map<VcsUser, List<String>>, consumer: (String) -> Unit)
}