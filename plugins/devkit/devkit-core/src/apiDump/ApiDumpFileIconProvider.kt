// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.apiDump

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.devkit.DevKitIcons
import javax.swing.Icon

internal class ApiDumpFileIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return null

    when {
      ApiDumpUtil.isApiDumpFile(file) -> return DevKitIcons.ApiDump
      ApiDumpUtil.isApiDumpUnreviewedFile(file) -> return DevKitIcons.ApiDumpUnreviewed
      ApiDumpUtil.isApiDumpExperimentalFile(file) -> return DevKitIcons.ApiDumpExperimental
      ApiDumpUtil.isExposedThirdPartyFile(file) -> return DevKitIcons.ApiDumpExposed
      ApiDumpUtil.isExposedPrivateApiFile(file) -> return DevKitIcons.ApiDumpExposed
      else -> return null
    }
  }
}