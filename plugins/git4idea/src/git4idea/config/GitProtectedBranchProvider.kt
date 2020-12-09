// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface GitProtectedBranchProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GitProtectedBranchProvider> = ExtensionPointName.create("Git4Idea.gitProtectedBranchProvider")
    @JvmStatic
    fun getProtectedBranchPatterns(project: Project): List<String> {
      return EP_NAME.extensionList.flatMap { provider -> provider.doGetProtectedBranchPatterns(project)}
    }
  }

  /**
   * Return protected branch patterns in regex format.
   */
  fun doGetProtectedBranchPatterns(project: Project): List<String>
}
