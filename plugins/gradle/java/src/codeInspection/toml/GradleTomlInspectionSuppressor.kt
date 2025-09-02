// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.toml

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.prevLeaf
import com.intellij.util.asSafely
import org.toml.lang.psi.TomlKeySegment
import java.util.regex.Pattern

internal class GradleTomlInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element !is TomlKeySegment) return false
    val commentBeforeElement = element.prevLeaf { it !is PsiWhiteSpace }
      .asSafely<PsiComment>() ?: return false
    val matcher = getSuppressPattern().matcher(commentBeforeElement.text)
    return matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<out SuppressQuickFix?> {
    if (element == null) return SuppressQuickFix.EMPTY_ARRAY
    return arrayOf(GradleTomlSuppressByCommentFix(toolId))
  }

  private fun getSuppressPattern() = Pattern.compile("#" + SuppressionUtil.COMMON_SUPPRESS_REGEXP)
}

