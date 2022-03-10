// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GrInspectionUIUtil")
package org.jetbrains.plugins.groovy.codeInspection.utils

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.SeparatorFactory
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.getDisableableFileTypes
import java.awt.event.ItemEvent
import javax.swing.JComponent

internal fun enhanceInspectionToolPanel(tool: LocalInspectionTool, container: MutableSet<String>, actualPanel: JComponent?): JComponent? {
  val disableableFileTypes = getDisableableFileTypes(tool.javaClass)
  if (actualPanel == null && disableableFileTypes.isEmpty()) {
    return null
  }
  val component = actualPanel ?: MultipleCheckboxOptionsPanel(tool)
  if (disableableFileTypes.isNotEmpty()) {
    component.add(SeparatorFactory.createSeparator(GroovyBundle.message("inspection.separator.disable.in.file.types"), null))
    for (fileType in disableableFileTypes) {
      val checkBox = JBCheckBox(fileType.displayName, container.contains(fileType.name))
      component.add(checkBox)
      checkBox.addItemListener { event ->
        if (event.stateChange == ItemEvent.SELECTED) {
          container.add(fileType.name)
        }
        else {
          container.remove(fileType.name)
        }
      }
    }
  }
  return component
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
