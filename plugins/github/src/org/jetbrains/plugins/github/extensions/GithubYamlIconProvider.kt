// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

val GITHUB_ACTION_SCHEMA_NAMES: Set<String> = setOf("github-action")
val GITHUB_WORKFLOW_SCHEMA_NAMES: Set<String> = setOf("github-workflow")

internal class GithubYamlIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {

    if (isGithubActionsFile(file, project)) {
      return AllIcons.Vcs.Vendors.Github
    }

    return null
  }
}
