// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection

import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.codeInspection.SuppressionUtilCore
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

internal class SuppressByGroovyFileCommentFix(toolId: String) : SuppressByCommentFix(toolId, GroovyFile::class.java) {

  override fun getText(): @IntentionName String = AnalysisBundle.message("suppress.inspection.file")

  override fun getContainer(context: PsiElement?): PsiElement? = context?.containingFile

  override fun createSuppression(project: Project, element: PsiElement, container: PsiElement) {
    val file = container as GroovyFile
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
    val commentText = "//" + SuppressionUtil.FILE_PREFIX + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID
    val anchor = fileComments(file).lastOrNull()
    if (anchor == null) {
      document.insertString(0, "$commentText\n")
    }
    else {
      document.insertString(anchor.textRange.endOffset, "\n$commentText")
    }
  }
}

/**
 * Skips comments and whitespaces at the start of the file, and returns last found comment.
 * This includes skipping hash bang, copyright notice, and/or other suppressions.
 */
private fun fileComments(file: GroovyFile): Sequence<PsiComment> = sequence {
  var child = file.firstChild
  while (child != null) {
    val elementType = child.node.elementType
    if (elementType === GroovyElementTypes.NL || elementType === TokenType.WHITE_SPACE) {
      child = child.nextSibling
    }
    else if (child is PsiComment) {
      yield(child)
      child = child.getNextSibling()
    }
    else {
      break
    }
  }
}

internal fun fileLevelSuppression(place: PsiElement, toolId: String): PsiElement? {
  val containingFile = place.containingFile as? GroovyFile ?: return null
  for (comment in fileComments(containingFile)) {
    val text = comment.text
    val matcher = SuppressionUtil.SUPPRESS_IN_FILE_LINE_COMMENT_PATTERN.matcher(text)
    if (matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)) {
      return comment
    }
  }
  return null
}
