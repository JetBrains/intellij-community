// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.PropertiesInspectionBase
import com.intellij.lang.properties.parsing.PropertiesTokenTypes
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import java.text.ChoiceFormat

class DevKitPropertiesQuotesValidationInspection : PropertiesInspectionBase() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is Property) return
        val value = element.value ?: return

        val quotedParam = checkQuotedParam(value) ?: return

        val paramString = "{$quotedParam}"
        val idx = value.indexOf(paramString) + element.getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS)!!.startOffsetInParent
        holder.registerProblem(element, TextRange(idx, idx + paramString.length),
                               DevKitI18nBundle.message("inspection.message.wrong.quotes.around.parameter.reference", paramString))
      }
    }
  }

  fun checkQuotedParam(pattern: String): Int? {
    var raw = true
    var inQuote = false
    var i = 0
    var paramSB : StringBuilder? = null
    var level = 0
    while (i < pattern.length) {
      val ch = pattern[i]
      if (raw) {
        if (ch == '\'') {
          if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
            i++
          }
          else {
            if (!inQuote) {
              paramSB = StringBuilder()
            }
            else {
              val quotedStr = paramSB.toString()
              if (quotedStr.startsWith('{') && quotedStr.endsWith('}')) {
                try {
                  return Integer.parseInt(quotedStr.trimStart { it == '{'}.trimEnd { it == '}'})
                }
                catch (ignored: NumberFormatException) { }
              }
              paramSB = null
            }
            inQuote = !inQuote
          }
        }
        else if (ch == '{' && !inQuote) {
          raw = false
        }
        else if (inQuote) {
          paramSB!!.append(ch)
        }
      }
      else {
        paramSB?.append(ch)
        if (inQuote) {
          if (ch == '\'') {
            inQuote = false
          }
        }
        else when (ch) {
          '{' -> level++
          ',' -> {
            @NonNls val prefix = "choice,"
            if (pattern.substring(i + 1).trim().startsWith(prefix)) {
              i += prefix.length + 1
              paramSB = StringBuilder()
            }
          }
          '}' -> {
            if (level -- == 0) {
              try {
                val choiceFormat = ChoiceFormat(paramSB.toString().trimEnd { it == '}' })
                for (format in choiceFormat.formats) {
                  return checkQuotedParam(format as String) ?: continue
                }
              }
              catch (ignore: IllegalArgumentException) {
                //illegal choice format, do nothing for now
              }
              paramSB = null
              raw = true
            }
          }
          '\'' -> inQuote = true
        }
      }
      ++i
    }
    return null
  }
}