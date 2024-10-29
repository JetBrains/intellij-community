// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.apiDump

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile

internal class ApiDumpGeneratedSourcesFilter : GeneratedSourcesFilter() {

  override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) &&
           ApiDumpUtil.isApiDumpFile(file) || ApiDumpUtil.isApiDumpUnreviewedFile(file) ||
           ApiDumpUtil.isExposedThirdPartyFile(file) || ApiDumpUtil.isExposedPrivateApiFile(file)
  }

  override fun getNotificationText(file: VirtualFile, project: Project): @NlsContexts.LinkLabel String? {
    return org.jetbrains.idea.devkit.DevKitBundle.message("api.dump.generated.sources.filter.notification")
  }
}