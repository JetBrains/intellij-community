// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.configurable.VcsOptionsTopHitProviderBase
import git4idea.GitVcs

internal class GitOptionsTopHitProvider : VcsOptionsTopHitProviderBase() {
  override fun getId(): String {
    return "vcs"
  }

  override fun getOptions(project: Project): Collection<OptionDescription> {
    if (isEnabled(project, GitVcs.getKey())) {
      return gitOptionDescriptors(project)
    }
    return emptyList()
  }
}