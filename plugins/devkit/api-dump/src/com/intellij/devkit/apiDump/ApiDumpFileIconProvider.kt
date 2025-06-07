// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class ApiDumpFileIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return null

    when {
      ApiDumpUtil.isApiDumpFile(file) -> return DevkitApiDumpIcons.ApiDump
      ApiDumpUtil.isApiDumpUnreviewedFile(file) -> return DevkitApiDumpIcons.ApiDumpUnreviewed
      ApiDumpUtil.isApiDumpExperimentalFile(file) -> return DevkitApiDumpIcons.ApiDumpExperimental
      ApiDumpUtil.isExposedThirdPartyFile(file) -> return DevkitApiDumpIcons.ApiDumpExposed
      ApiDumpUtil.isExposedPrivateApiFile(file) -> return DevkitApiDumpIcons.ApiDumpExposed
      else -> return null
    }
  }
}