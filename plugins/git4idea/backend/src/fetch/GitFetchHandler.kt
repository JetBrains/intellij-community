// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

interface GitFetchHandler {
    companion object {
      @JvmField
      val EP_NAME: ExtensionPointName<GitFetchHandler> = ExtensionPointName.create("Git4Idea.gitFetchHandler")
      @JvmStatic
      fun afterSuccessfulFetch(project: Project, fetches: Map<GitRepository, List<GitRemote>>, indicator: ProgressIndicator) {
        EP_NAME.extensionList.forEach { handler -> handler.doAfterSuccessfulFetch(project, fetches, indicator) }
      }
    }

  fun doAfterSuccessfulFetch(project: Project, fetches: Map<GitRepository, List<GitRemote>>, indicator: ProgressIndicator)
}
