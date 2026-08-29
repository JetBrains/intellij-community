// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.contentReport

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourceFilterHyperLinkAction
import com.intellij.openapi.roots.GeneratedSourceFilterNotification
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile

/**
 * Marks the two dev-distribution statements a packaging test writes, so that the editor says who owns the file.
 *
 * Both carry the same rule and a different text, because a reader who wants to edit one needs a different answer.
 * `plugin-content.yaml` is the whole content report of a plugin. `dev-dist.yaml` is the residue: what the JPS-to-Bazel
 * converter cannot derive from the project model, which is the file `AllProductsPackagingTest` patches when a layout
 * change makes the derivation wrong.
 */
internal class ContentReportGeneratedSourcesFilter : GeneratedSourcesFilter() {
  override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) && file.name in GENERATED_FILE_NAMES
  }

  override fun getNotification(file: VirtualFile, project: Project): GeneratedSourceFilterNotification {
    val text = if (file.name == DEV_DIST_RESIDUE_FILE_NAME) {
      "Do not modify manually, the dev-distribution residue must be changed by AllProductsPackagingTest"
    }
    else {
      "Do not modify manually, content report must be changed by AllProductsPackagingTest"
    }
    return GeneratedSourceFilterNotification(
      text = text,
      actions = listOf(GeneratedSourceFilterHyperLinkAction(
        text = "Distribution Content Approving",
        link = "https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving",
      ))
    )
  }
}

private const val DEV_DIST_RESIDUE_FILE_NAME: String = "dev-dist.yaml"

private val GENERATED_FILE_NAMES: Set<String> = setOf("plugin-content.yaml", DEV_DIST_RESIDUE_FILE_NAME)