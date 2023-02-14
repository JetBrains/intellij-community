// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.annotators

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import org.editorconfig.language.highlighting.EditorConfigSyntaxHighlighter
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.*
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor

class EditorConfigAnnotatorVisitor(private val holder: AnnotationHolder) : EditorConfigVisitor() {
  override fun visitQualifiedOptionKey(key: EditorConfigQualifiedOptionKey) {
    checkEdgeDots(key.firstChild, key.firstChild.nextSibling)
    checkEdgeDots(key.lastChild, key.lastChild.prevSibling)
    checkInnerDots(key)
  }

  private fun checkInnerDots(key: EditorConfigQualifiedOptionKey) {
    var firstDot: PsiElement? = null
    var lastDot: PsiElement? = null
    SyntaxTraverser.psiTraverser().children(key).forEach {
      when {
        it.node.elementType == EditorConfigElementTypes.DOT -> {
          if (firstDot == null) {
            firstDot = it
          }
          lastDot = it
        }
        firstDot != lastDot -> {
          val message = EditorConfigBundle.get("annotator.error.multiple-dots")
          val start = firstDot!!.textRange.startOffset
          val end = lastDot!!.textRange.endOffset // + lastDot!!.textLength
          val range = TextRange.create(start, end)
          holder.newAnnotation(HighlightSeverity.ERROR, message).range(range).create()
          firstDot = null
          lastDot = null
        }
        else -> {
          firstDot = null
          lastDot = null
        }
      }
    }
  }

  private fun checkEdgeDots(edgeElement: PsiElement, neighbourElement: PsiElement?) {
    if (edgeElement.node.elementType != EditorConfigElementTypes.DOT) return
    if (neighbourElement?.node?.elementType == EditorConfigElementTypes.DOT) return
    val message = EditorConfigBundle.get("annotator.error.key.dangling-dot")
    holder.newAnnotation(HighlightSeverity.ERROR, message).range(edgeElement).create()
  }

  override fun visitOption(option: EditorConfigOption) {
    checkLineBreaks(option)
  }

  private fun checkLineBreaks(option: EditorConfigOption) {
    if (!option.textContains('\n')) return
    val message = EditorConfigBundle["annotator.error.option.suspicious.line.break"]
    holder.newAnnotation(HighlightSeverity.ERROR, message).range(option).create()
  }

  override fun visitFlatOptionKey(flatKey: EditorConfigFlatOptionKey) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(flatKey).textAttributes(EditorConfigSyntaxHighlighter.PROPERTY_KEY).create()
  }

  override fun visitQualifiedKeyPart(keyPart: EditorConfigQualifiedKeyPart) {
    val descriptor = keyPart.getDescriptor(false)
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(keyPart).textAttributes(
      if (descriptor is EditorConfigDeclarationDescriptor) EditorConfigSyntaxHighlighter.PROPERTY_KEY
      else EditorConfigSyntaxHighlighter.KEY_DESCRIPTION).create()
  }

  override fun visitOptionValueIdentifier(identifier: EditorConfigOptionValueIdentifier) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(identifier).textAttributes(EditorConfigSyntaxHighlighter.PROPERTY_VALUE).create()
  }

  override fun visitRawOptionValue(rawOptionValue: EditorConfigRawOptionValue) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(rawOptionValue).textAttributes(EditorConfigSyntaxHighlighter.PROPERTY_VALUE).create()
  }

  override fun visitFlatPattern(flatPattern: EditorConfigFlatPattern) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(flatPattern).textAttributes(EditorConfigSyntaxHighlighter.PATTERN).create()
    if (!flatPattern.textContains('\\')) return

    val text = flatPattern.text
    val offset = flatPattern.textOffset

    var index = 0
    while (index < text.length) {
      if (text[index] == '\\') {
        val range = TextRange(offset + index, offset + index + 2)
        index += 1
        if (EditorConfigSyntaxHighlighter.VALID_ESCAPES.contains(text[index])) {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE).create()
        }
        else {
          val message = EditorConfigBundle["annotator.error.illegal.char.escape"]
          holder.newAnnotation(HighlightSeverity.INFORMATION, message).range(range).textAttributes(EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE).create()
        }
      }
      index += 1
    }
  }

  override fun visitAsteriskPattern(pattern: EditorConfigAsteriskPattern) {
    special(pattern)
  }

  override fun visitDoubleAsteriskPattern(pattern: EditorConfigDoubleAsteriskPattern) {
    special(pattern)
  }

  private fun special(pattern: PsiElement) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(pattern).textAttributes(
      EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL).create()
  }

  override fun visitCharClassExclamation(exclamation: EditorConfigCharClassExclamation) {
    special(exclamation)
  }

  override fun visitQuestionPattern(pattern: EditorConfigQuestionPattern) {
    special(pattern)
  }

  override fun visitCharClassLetter(letter: EditorConfigCharClassLetter) = when {
    !letter.isEscape -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(letter).textAttributes(EditorConfigSyntaxHighlighter.PATTERN).create()
    letter.isValidEscape -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(letter).textAttributes(EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE).create()
    else -> holder.newAnnotation(HighlightSeverity.INFORMATION, EditorConfigBundle.get("annotator.error.illegal.char.escape")).range(letter).textAttributes(EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE).create()
  }

  override fun visitRootDeclarationKey(key: EditorConfigRootDeclarationKey) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(key).textAttributes(EditorConfigSyntaxHighlighter.PROPERTY_KEY).create()
  }

  override fun visitRootDeclarationValue(value: EditorConfigRootDeclarationValue) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(value).textAttributes(EditorConfigSyntaxHighlighter.PROPERTY_VALUE).create()
  }
}
