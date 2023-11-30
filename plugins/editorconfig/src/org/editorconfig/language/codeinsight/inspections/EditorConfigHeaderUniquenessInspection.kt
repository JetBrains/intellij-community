// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigMergeSectionsQuickFix
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveSectionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.iterateTypedSiblingsBackward
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.iterateTypedSiblingsForward
import org.editorconfig.language.util.isEquivalentTo

internal fun findDuplicateSection(section: EditorConfigSection): EditorConfigSection? {
  iterateTypedSiblingsBackward(section) {
    if (it !== section && it.header isEquivalentTo section.header) {
      return it
    }
  }
  iterateTypedSiblingsForward(section) {
    if (it !== section && it.header isEquivalentTo section.header) {
      return it
    }
  }
  return null
}

internal class EditorConfigHeaderUniquenessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      val section = header.section
      val duplicate = findDuplicateSection(section) ?: return
      val messageId =
        if (duplicate.header.textMatches(section.header)) "inspection.section.uniqueness.message"
        else "inspection.section.uniqueness.complex.message"

      val message = EditorConfigBundle.get(messageId, duplicate.header.text)
      if (header.section.optionList.isEmpty()) {
        holder.registerProblem(header, message, EditorConfigRemoveSectionQuickFix())
      }
      else {
        holder.registerProblem(header, message, EditorConfigRemoveSectionQuickFix(), EditorConfigMergeSectionsQuickFix())
      }
    }
  }

}