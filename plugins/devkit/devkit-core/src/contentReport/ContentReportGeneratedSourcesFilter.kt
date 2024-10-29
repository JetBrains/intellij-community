// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.contentReport

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourceFilterHyperLinkAction
import com.intellij.openapi.roots.GeneratedSourceFilterNotification
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile

private class ContentReportGeneratedSourcesFilter : GeneratedSourcesFilter() {
  override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) && file.name == "plugin-content.yaml"
  }

  override fun getNotification(file: VirtualFile, project: Project): GeneratedSourceFilterNotification {
    return GeneratedSourceFilterNotification(
      text = "Do not modify manually, content report must be changed by IdeaUltimatePackagingTest",
      actions = listOf(GeneratedSourceFilterHyperLinkAction(
        text = "Distribution Content Approving",
        link = "https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving",
      ))
    )
  }
}