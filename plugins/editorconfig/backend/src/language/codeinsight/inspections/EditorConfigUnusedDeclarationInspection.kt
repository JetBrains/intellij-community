// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.util.EditorConfigIdentifierUtil

class EditorConfigUnusedDeclarationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitPsiElement(element: PsiElement) {
      if (element !is EditorConfigDescribableElement) return
      val descriptor = element.getDescriptor(false) as? EditorConfigDeclarationDescriptor ?: return
      if (!descriptor.needsReferences) return
      val references = EditorConfigIdentifierUtil.findReferences(element.section, descriptor.id, element.text)
      if (references.isNotEmpty()) return

      val message = EditorConfigBundle["inspection.declaration.unused.message"]
      holder.registerProblem(
        element,
        message,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        EditorConfigRemoveOptionQuickFix()
      )
    }
  }
}
