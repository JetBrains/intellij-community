// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references

import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.util.text.findTextRange
import org.jetbrains.annotations.ApiStatus

const val LANGUAGE_PREFIX: String = "language="

object LanguageInjectionUtil {
  @ApiStatus.Experimental
  fun languageRange(psiComment: PsiComment): TextRange? {
    val commentBody = commentBody(psiComment).trim()
    if (!commentBody.startsWith(LANGUAGE_PREFIX)) return null

    val startOffset = psiComment.text.findTextRange(LANGUAGE_PREFIX)!!.endOffset
    val endOffset = psiComment.text.findTextRange(commentBody)!!.endOffset
    return TextRange(startOffset, endOffset)
  }

  @ApiStatus.Experimental
  fun commentBody(comment: PsiComment): String {
    val text = comment.text
    val commenter = LanguageCommenters.INSTANCE.forLanguage(comment.language) ?: return text
    for (lineCommentPrefix in commenter.lineCommentPrefixes) {
      if (text.startsWith(lineCommentPrefix)) return text.substring(lineCommentPrefix.length)
    }
    return text.let { text -> commenter.blockCommentPrefix?.let { text.removePrefix(it) } ?: text }
      .let { text -> commenter.blockCommentSuffix?.let { text.removeSuffix(it) } ?: text }
  }
}