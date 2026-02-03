// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.metaInformation

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo

internal fun isMetaInformationFile(psiFile: PsiFile): Boolean {
  val virtualFile = psiFile.virtualFile ?: return false
  return isMetaInformationFile(virtualFile, psiFile.project)
}

internal fun isMetaInformationFile(file: VirtualFile, project: Project): Boolean {
  if (file.name != "metaInformation.json") return false
  val descriptionDirectory = file.parent ?: return false
  val module = ModuleUtilCore.findModuleForFile(file, project)?: return false
  val descriptionDirs = InspectionDescriptionInfo.allDescriptionDirs(module)
  return descriptionDirs.any {
    it.virtualFile == descriptionDirectory
  }
}
