// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.features

import com.intellij.cce.evaluable.completion.CompletionActionsInvoker.Companion.CCE_SESSION_UID
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

class CCEContextFeatureProvider(private val logLocation: Boolean) : ContextFeatureProvider {
  override fun getName(): String = "cce"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val uuid = UUID.randomUUID().toString()
    val uuidFeature = createStringFeature(uuid)
    val features = mutableMapOf(CCE_SESSION_UID to uuidFeature)

    if (!logLocation) return features

    val vFile = environment.parameters.originalFile.virtualFile
    val relativeFile = vFile?.let { getRelativePath(it, environment.lookup.project) }

    if (relativeFile != null)
      features["location"] = createStringFeature("$relativeFile:${environment.parameters.offset}")

    return features
  }

  companion object {

    private fun getRelativePath(vFile: VirtualFile, project: Project): String? {
      val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(vFile)
      // relative from repository root
      if (vcsRoot != null) {
        return VfsUtilCore.getRelativePath(vFile, vcsRoot.path)
      }

      // otherwise, return relative from content root
      val module = ProjectFileIndex.getInstance(project).getModuleForFile(vFile, false) ?: return null
      return ModuleRootManager.getInstance(module).contentRoots
        .mapNotNull { root -> VfsUtilCore.getRelativePath(vFile, root) }
        .singleOrNull()
    }
  }
}

fun createStringFeature(text: String): MLFeatureValue {
  // todo find more reliable way to create string feature
  return Class.forName("com.intellij.codeInsight.completion.ml.MLFeatureValue\$CategoricalValue")
    .getConstructor(String::class.java)
    .newInstance(text) as MLFeatureValue
}