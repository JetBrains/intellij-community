// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.psi.reference.findParents

class EditorConfigOptionRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitFlatOptionKey(flatOptionKey: EditorConfigFlatOptionKey) {
      val option = flatOptionKey.option
      val parents = flatOptionKey.findParents()
      if (parents.isEmpty()) return
      val parentOptions = parents.map(EditorConfigFlatOptionKey::option)
      if (!parentOptions.all { haveEqualValues(option, it) }) return

      val message = EditorConfigBundle.get("inspection.option.redundant.message", option.name ?: "" )
      holder.registerProblem(
        flatOptionKey,
        message,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        EditorConfigRemoveOptionQuickFix()
      )
    }
  }

  private fun haveEqualValues(first: EditorConfigOption, second: EditorConfigOption): Boolean {
    val firstValues = PsiTreeUtil
      .findChildrenOfType(first, EditorConfigOptionValueIdentifier::class.java)
    val secondValues = PsiTreeUtil
      .findChildrenOfType(second, EditorConfigOptionValueIdentifier::class.java)
    if (firstValues.size != secondValues.size) return false
    return firstValues.zip(secondValues)
      .all { it.first.textMatches(it.second) }
  }
}
