// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.apiDump

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class ApiDumpFileIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return null

    when {
      ApiDumpUtil.isApiDumpFile(file) -> return AllIcons.Ide.HectorOn
      ApiDumpUtil.isApiDumpUnreviewedFile(file) -> return AllIcons.Ide.HectorOff
      ApiDumpUtil.isApiDumpExperimentalFile(file) -> return AllIcons.Ide.HectorOff
      ApiDumpUtil.isExposedThirdPartyFile(file) -> return AllIcons.Ide.HectorSyntax
      ApiDumpUtil.isExposedPrivateApiFile(file) -> return AllIcons.Ide.HectorSyntax
      else -> return null
    }
  }
}