// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import icons.GithubIcons
import javax.swing.Icon

internal class GHPRFileType : FileType {
  override fun getName() = "GithubPullRequest"
  override fun getDescription() = "Github Pull Request"
  override fun getDefaultExtension() = ""
  override fun getIcon(): Icon? = GithubIcons.PullRequestOpen
  override fun isBinary() = true
  override fun isReadOnly() = true
  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

  companion object {
    @JvmStatic
    val INSTANCE: FileType = GHPRFileType()
  }
}