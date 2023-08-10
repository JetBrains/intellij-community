// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Represents a file/directory where IntelliJ project is stored.
 */
sealed class JpsProjectConfigLocation {
  val baseDirectoryUrlString: String
    get() = baseDirectoryUrl.url

  /**
   * Same as [Project.getProjectFilePath]
   */
  abstract val projectFilePath: String

  abstract val baseDirectoryUrl: VirtualFileUrl

  data class DirectoryBased(val projectDir: VirtualFileUrl, val ideaFolder: VirtualFileUrl) : JpsProjectConfigLocation() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = projectDir

    override val projectFilePath: String
      get() = JpsPathUtil.urlToPath(ideaFolder.append("misc.xml").url)
  }

  data class FileBased(val iprFile: VirtualFileUrl, val iprFileParent: VirtualFileUrl) : JpsProjectConfigLocation() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = iprFileParent

    override val projectFilePath: String
      get() = JpsPathUtil.urlToPath(iprFile.url)
  }
}