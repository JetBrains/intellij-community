// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveSpacesQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigSpaceInHeaderInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      if (PsiTreeUtil.hasErrorElements(header)) return
      val spaces = findSuspiciousSpaces(header)
      if (spaces.isEmpty()) return
      val message = EditorConfigBundle["inspection.space.in.header.message"]
      holder.registerProblem(
        header,
        message,
        EditorConfigRemoveSpacesQuickFix()
      )
    }
  }

}

internal fun findSuspiciousSpaces(header: EditorConfigHeader) =
  SyntaxTraverser.psiTraverser(header).filter(PsiWhiteSpace::class.java).toList()
