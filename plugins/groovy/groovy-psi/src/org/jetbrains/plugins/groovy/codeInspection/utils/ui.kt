// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GrInspectionUIUtil")
package org.jetbrains.plugins.groovy.codeInspection.utils

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.options.OptCheckbox
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.getDisableableFileTypes

internal fun enhanceInspectionToolPanel(tool: LocalInspectionTool, pane: OptPane): OptPane {
  val disableableFileTypes = getDisableableFileTypes(tool.javaClass)
  if (disableableFileTypes.isEmpty()) return pane
  val checkboxes = arrayListOf<OptCheckbox>()
  for (fileType in disableableFileTypes) {
    checkboxes.add(OptPane.checkbox(fileType.name, fileType.displayName))
  }
  return OptPane(
    pane.components +
    OptPane.group(GroovyBundle.message("inspection.separator.disable.in.file.types"), *checkboxes.toTypedArray()).prefix("fileType")
  )
}

internal fun getFileTypeController(explicitlyEnabledFileTypes: MutableSet<String>): OptionController {
  return OptionController.of(explicitlyEnabledFileTypes::contains,
    { bindId: String, value: Any ->
      if (value as Boolean) {
        explicitlyEnabledFileTypes.add(bindId)
      }
      else {
        explicitlyEnabledFileTypes.remove(bindId)
      }
    })
}

internal fun checkInspectionEnabledByFileType(tool: LocalInspectionTool, element: PsiElement, explicitlyAllowedFileTypes: Set<String>) : Boolean {
  val disableableFileTypes = getDisableableFileTypes(tool.javaClass)
  if (disableableFileTypes.isEmpty()) {
    return true
  }
  val forbiddenFileTypes = disableableFileTypes.filter { it.name !in explicitlyAllowedFileTypes }
  val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return true
  val registry = FileTypeRegistry.getInstance()
  for (forbiddenFileType in forbiddenFileTypes) {
    if (registry.isFileOfType(virtualFile, forbiddenFileType)) {
      return false
    }
  }
  return true
}
