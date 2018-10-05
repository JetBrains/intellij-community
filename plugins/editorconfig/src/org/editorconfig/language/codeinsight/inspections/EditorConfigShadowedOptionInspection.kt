// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findShadowingSections

class EditorConfigShadowedOptionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitOption(option: EditorConfigOption) {
      findShadowingSections(option.section)
        .asSequence()
        .flatMap { it.optionList.asSequence() }
        .dropWhile { it !== option }
        .drop(1)
        .firstOrNull { equalOptions(option, it) }
        ?.apply {
          val message = EditorConfigBundle["inspection.option.shadowed.message"]
          holder.registerProblem(option, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, EditorConfigRemoveOptionQuickFix())
        }
    }
  }

  companion object {
    fun equalOptions(first: EditorConfigOption, second: EditorConfigOption): Boolean {
      val firstDescriptor = first.getDescriptor(false) ?: return false
      val firstDeclarations = findDeclarations(first)
      if (first.keyParts.size != second.keyParts.size) return false
      val secondDescriptor = second.getDescriptor(false)
      if (firstDescriptor != secondDescriptor) return false
      if (EditorConfigDescriptorUtil.isConstant(firstDescriptor.key)) return true
      val secondDeclarations = findDeclarations(second)
      return equalToIgnoreCase(firstDeclarations, secondDeclarations)
    }

    private fun equalToIgnoreCase(first: List<String>, second: List<String>): Boolean {
      if (first.size != second.size) return false
      return first.zip(second).all(::equalToIgnoreCase)
    }

    private fun equalToIgnoreCase(pair: Pair<String, String>) =
      pair.first.equals(pair.second, true)

    private fun findDeclarations(option: EditorConfigOption): List<String> {
      val descriptor = option.getDescriptor(false) ?: return emptyList()
      val keyDescriptor = descriptor.key as? EditorConfigQualifiedKeyDescriptor ?: return emptyList()
      if (option.keyParts.size != keyDescriptor.children.size) throw IllegalStateException()
      return option.keyParts.filterIndexed { index, _ ->
        EditorConfigDescriptorUtil.isVariable(keyDescriptor.children[index])
      }
    }
  }
}
