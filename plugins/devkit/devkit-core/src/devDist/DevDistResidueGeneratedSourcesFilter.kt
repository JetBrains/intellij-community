// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.devDist

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourceFilterHyperLinkAction
import com.intellij.openapi.roots.GeneratedSourceFilterNotification
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile

/** The dev-distribution residue, beside a plugin main module that needs one. */
private const val DEV_DIST_RESIDUE_FILE_NAME: String = "dev-dist.yaml"

/**
 * Marks the dev-distribution residue a packaging test writes, so that the editor says who owns the file.
 *
 * `dev-dist.yaml` states what the JPS-to-Bazel converter cannot derive from the project model.
 * `AllProductsPackagingTest` patches the file when a layout change makes the derivation wrong.
 */
internal class DevDistResidueGeneratedSourcesFilter : GeneratedSourcesFilter() {
  override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) && file.name == DEV_DIST_RESIDUE_FILE_NAME
  }

  override fun getNotification(file: VirtualFile, project: Project): GeneratedSourceFilterNotification {
    return GeneratedSourceFilterNotification(
      text = "Do not modify manually, the dev-distribution residue must be changed by AllProductsPackagingTest",
      actions = listOf(GeneratedSourceFilterHyperLinkAction(
        text = "Distribution Content Approving",
        link = "https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving",
      ))
    )
  }
}
