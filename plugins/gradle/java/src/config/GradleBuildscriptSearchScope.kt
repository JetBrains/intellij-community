// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleBuildscriptSearchScope(project: Project) : GlobalSearchScope(project) {
  override fun contains(file: VirtualFile): Boolean {
    return GradleConstants.EXTENSION == file.extension || file.name.endsWith(GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)
  }
  override fun isSearchInModuleContent(aModule: Module) = true
  override fun isSearchInLibraries() = false

  override fun getDisplayName(): String {
    return GradleInspectionBundle.message("gradle.buildscript.search.scope")
  }
}