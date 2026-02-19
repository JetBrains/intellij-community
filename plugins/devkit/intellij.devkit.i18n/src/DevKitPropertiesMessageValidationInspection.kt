// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.PropertiesInspectionBase
import com.intellij.lang.properties.parsing.PropertiesTokenTypes
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.siyeh.ig.bugs.IncorrectMessageFormatInspection
import com.siyeh.ig.format.MessageFormatUtil
import com.siyeh.ig.format.MessageFormatUtil.MessageFormatError
import com.siyeh.ig.format.MessageFormatUtil.MessageFormatErrorType
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil

private val SKIPPED_ERROR_TYPES: Set<MessageFormatErrorType> = setOf(MessageFormatErrorType.INDEX_NEGATIVE,
                                                                     MessageFormatErrorType.UNPARSED_INDEX)

internal class DevKitPropertiesMessageValidationInspection : PropertiesInspectionBase() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is Property) return
        val unescapedValue = element.unescapedValue ?: return
        val value = element.value ?: return

        if (!(unescapedValue.contains("{0}") || unescapedValue.contains("{0,") ||
              unescapedValue.contains("{1}") || unescapedValue.contains("{1,"))) return
        val messageFormatResult = MessageFormatUtil.checkFormat(unescapedValue)
        if (messageFormatResult.valid) {
          return
        }
        val startIndex = element.getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS)?.startOffsetInParent ?: return
        for (error in messageFormatResult.errors) {
          if (SKIPPED_ERROR_TYPES.contains(error.errorType)) continue
          val relatedText = getRelatedText(unescapedValue, error) ?: continue
          if (unescapedValue == value) {
            holder.registerProblem(element, TextRange(error.fromIndex + startIndex, error.toIndex + startIndex),
                                   IncorrectMessageFormatInspection.getMessageFormatTemplate(error.errorType, relatedText))

          }
          else {
            var startedCount = 0
            var nextRelatedTestIndex = unescapedValue.indexOf(relatedText, 0)
            while (nextRelatedTestIndex != -1 && nextRelatedTestIndex != error.fromIndex) {
              startedCount++
              nextRelatedTestIndex = unescapedValue.indexOf(relatedText, nextRelatedTestIndex + 1)
            }
            if (nextRelatedTestIndex == -1) continue
            var nextStartIndex = value.indexOf(relatedText, 0)
            for (i in 0 until startedCount) {
              nextStartIndex = value.indexOf(relatedText, nextStartIndex + 1)
            }
            if (nextStartIndex == -1) continue
            holder.registerProblem(element,
                                   TextRange(nextStartIndex + startIndex, nextStartIndex + startIndex + error.toIndex - error.fromIndex),
                                   IncorrectMessageFormatInspection.getMessageFormatTemplate(error.errorType, relatedText))
          }
        }

        if (messageFormatResult.errors.asSequence()
            .map { it.errorType.severity }
            .any { it == MessageFormatUtil.ErrorSeverity.RUNTIME_EXCEPTION }) {
          return
        }
      }
    }
  }

  private fun getRelatedText(pattern: String, error: MessageFormatError): String? {
    return if (error.fromIndex < 0 || error.toIndex > pattern.length || error.toIndex < error.fromIndex) {
      null
    }
    else pattern.substring(error.fromIndex, error.toIndex)
  }
}