// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.suppression

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigRootDeclaration
import org.editorconfig.language.psi.EditorConfigSection
import java.util.regex.Pattern

class EditorConfigInspectionSuppressor : InspectionSuppressor {
  private val suppressCommentPattern = "[#;]" + SuppressionUtil.COMMON_SUPPRESS_REGEXP
  private val suppressPattern = Pattern.compile(suppressCommentPattern)

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
    isSuppressedFor(element, toolId, EditorConfigOption::class.java)
    || isSuppressedFor(element, toolId, EditorConfigSection::class.java)
    || isSuppressedFor(element, toolId, EditorConfigRootDeclaration::class.java)

  private fun isSuppressedFor(element: PsiElement, toolId: String, cls: Class<out PsiElement>): Boolean {
    val parent = PsiTreeUtil.getParentOfType(element, cls, false) ?: return false
    return getCommentsBefore(parent).any { isSuppressedInComment(it.text, toolId) }
  }

  private fun getCommentsBefore(element: PsiElement) = sequence<PsiElement> {
    var item: PsiElement? = element
    loop@ while (item != null) {
      item = item.prevSibling
      when (item) {
        is PsiWhiteSpace -> continue@loop
        is PsiComment -> yield(item)
        else -> break@loop
      }
    }
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<EditorConfigSuppressInspectionFix> = arrayOf(
    EditorConfigSuppressInspectionFix(toolId, EditorConfigBundle.get("suppress.inspection.option"), EditorConfigOption::class.java),
    EditorConfigSuppressInspectionFix(toolId, EditorConfigBundle.get("suppress.inspection.section"), EditorConfigSection::class.java)
  )

  private fun isSuppressedInComment(commentText: String, suppressId: String): Boolean {
    val matcher = suppressPattern.matcher(commentText)
    return matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), suppressId)
  }
}
