// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findShadowingSections

internal fun equalOptions(first: EditorConfigOption, second: EditorConfigOption): Boolean {
  val firstDescriptor = first.getDescriptor(false) ?: return false
  if (first.keyParts.size != second.keyParts.size) return false
  val secondDescriptor = second.getDescriptor(false)
  if (firstDescriptor != secondDescriptor) return false
  if (EditorConfigDescriptorUtil.isConstant(firstDescriptor.key)) return true
  if (!equalToIgnoreCase(findDeclarations(first), findDeclarations(second))) return false
  return equalToIgnoreCase(findConstants(first), findConstants(second))
}

private fun equalToIgnoreCase(first: List<String>, second: List<String>): Boolean {
  if (first.size != second.size) return false
  return first.zip(second).all(::equalToIgnoreCase)
}

private fun equalToIgnoreCase(pair: Pair<String, String>) =
  pair.first.equals(pair.second, true)

private fun findDeclarations(option: EditorConfigOption) = findMatching(option, EditorConfigDescriptorUtil::isVariable)

private fun findConstants(option: EditorConfigOption) = findMatching(option, EditorConfigDescriptorUtil::isConstant)

private fun findMatching(option: EditorConfigOption, filter: (EditorConfigDescriptor) -> Boolean): List<String> {
  val descriptor = option.getDescriptor(false) ?: return emptyList()
  val keyDescriptor = descriptor.key as? EditorConfigQualifiedKeyDescriptor ?: return emptyList()
  if (option.keyParts.size != keyDescriptor.children.size) throw IllegalStateException()
  return option.keyParts.filterIndexed { index, _ -> filter(keyDescriptor.children[index]) }
}

internal class EditorConfigShadowedOptionInspection : LocalInspectionTool() {
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

}
