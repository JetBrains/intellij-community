// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findShadowedSections

internal class EditorConfigShadowingOptionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitOption(option: EditorConfigOption) {
      findShadowedSections(option.section)
        .flatMap(EditorConfigSection::getOptionList)
        .dropLastWhile { it !== option }
        .dropLast(1)
        .lastOrNull { equalOptions(option, it) }
        ?.apply {
          val message = EditorConfigBundle["inspection.option.shadowing.message"]
          holder.registerProblem(findKey(option), message, EditorConfigRemoveOptionQuickFix())
        }
    }
  }

  private fun findKey(option: EditorConfigOption) =
    option.flatOptionKey
    ?: option.qualifiedOptionKey
    ?: throw IllegalStateException()
}
