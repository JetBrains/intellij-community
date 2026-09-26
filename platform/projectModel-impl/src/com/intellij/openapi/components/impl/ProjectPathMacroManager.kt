// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.project.ProjectStoreOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.PathMacroUtil

class ProjectPathMacroManager private constructor(
  private val projectFilePathPointer: () -> String?,
  private val basePathPointer: () -> String?,
  private val namePointer: (() -> String)? = null,
) : PathMacroManager(PathMacros.getInstance()) {

  constructor(project: Project) : this(
    projectFilePathPointer = { project.projectFilePath },
    basePathPointer = {
      if (project is ProjectStoreOwner) {
        FileUtilRt.toSystemIndependentName(
          project.componentStore.storeDescriptor.historicalProjectBasePath.toString()
        )
      }
      else {
        project.basePath
      }
    },
    namePointer = if (project.isDefault) null
    else {
      { project.name }
    }
  )

  override val expandMacroMap: ExpandMacroToPathMap
    get() {
      val result = super.expandMacroMap
      addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, basePathPointer())
      namePointer?.let { result.addMacroExpand(PathMacroUtil.PROJECT_NAME_MACRO_NAME, it()) }
      projectFilePathPointer()?.let { projectFile ->
        getAllMacros(projectFile).forEach { (key, value) ->
          result.addMacroExpand(key, value)
        }
      }
      return result
    }

  override fun computeReplacePathMap(): ReplacePathToMacroMap {
    val result = super.computeReplacePathMap()
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, basePathPointer(), null)
    projectFilePathPointer()?.let { projectFile ->
      getAllMacros(projectFile).forEach { (key, value) ->
        result.addMacroReplacement(value, key)
      }
    }
    return result
  }

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun createInstance(
      projectFilePathPointer: () -> String?,
      basePathPointer: () -> String?,
      namePointer: (() -> String)?,
    ): ProjectPathMacroManager {
      return ProjectPathMacroManager(projectFilePathPointer, basePathPointer, namePointer)
    }
  }
}