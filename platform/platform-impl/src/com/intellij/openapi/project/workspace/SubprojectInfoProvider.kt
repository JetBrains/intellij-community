// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.workspace

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SubprojectInfoProvider {
  fun getSubprojectPath(project: Project, file: VirtualFile): String?
  fun getSubprojectPath(module: Module): String?
  fun getSubprojectName(project: Project, file: VirtualFile): String?

  companion object {
    private val SUBPROJECT_INFO_PROVIDER_EP = ExtensionPointName.create<SubprojectInfoProvider>("com.intellij.subprojectInfoProvider")

    fun getSubprojectPath(project: Project, file: VirtualFile): String? = SUBPROJECT_INFO_PROVIDER_EP.extensionList.firstNotNullOfOrNull {
      it.getSubprojectPath(project, file)
    }

    fun getSubprojectPath(module: Module): String? = SUBPROJECT_INFO_PROVIDER_EP.extensionList.firstNotNullOfOrNull {
      it.getSubprojectPath(module)
    }

    fun getSubprojectName(project: Project, file: VirtualFile): String? = SUBPROJECT_INFO_PROVIDER_EP.extensionList.firstNotNullOfOrNull {
      it.getSubprojectName(project, file)
    }
  }
}

