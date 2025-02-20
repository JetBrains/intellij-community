// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.workspaceModel.core.fileIndex.OptionalExclusionContributor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object OptionalExclusionUtil {
  val EP_NAME: ExtensionPointName<OptionalExclusionContributor> =
    ExtensionPointName("com.intellij.workspaceModel.optionalExclusionContributor")

  @JvmStatic
  fun exclude(project: Project, fileOrDir: VirtualFile): Boolean {
    val url = fileOrDir.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
    for (exclusionContributor in EP_NAME.extensionList) {
      if (exclusionContributor.exclude(project, url)) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun cancelExclusion(project: Project, fileOrDir: VirtualFile): Boolean {
    val url = fileOrDir.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
    var cancelled = false
    for (exclusionContributor in EP_NAME.extensionList) {
      if (exclusionContributor.cancelExclusion(project, url)) {
        cancelled = true
      }
    }
    return cancelled
  }
}
