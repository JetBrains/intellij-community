// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.codeinsight.inspections.findDuplicateSection
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.services.EditorConfigElementFactory
import org.jetbrains.annotations.Nls

class EditorConfigMergeSectionsQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.section.merge-duplicate.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val header = descriptor.psiElement as? EditorConfigHeader ?: return
    val duplicateSection = findDuplicateSection(header.section) ?: return

    val elementFactory = EditorConfigElementFactory.getInstance(project)
    val replacementText = createReplacementText(header.section, duplicateSection)
    val replacement = elementFactory.createSection(replacementText)

    val manager = CodeStyleManager.getInstance(project) ?: return
    manager.performActionWithFormatterDisabled {
      duplicateSection.replace(replacement)
      header.section.delete()
    }
  }

  private fun createReplacementText(source: EditorConfigSection, destination: EditorConfigSection): String {
    val result = StringBuilder(destination.textLength + source.textLength)
    result.append(destination.text)
    for (child in source.children) {
      if (child is EditorConfigHeader) continue
      result.append('\n')
      result.append(child.text)
    }
    return result.toString()
  }
}
