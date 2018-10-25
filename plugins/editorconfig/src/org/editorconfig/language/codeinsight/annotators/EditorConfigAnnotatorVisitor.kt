// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.annotators

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import org.editorconfig.language.highlighting.EditorConfigSyntaxHighlighter
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.*
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor

class EditorConfigAnnotatorVisitor(private val holder: AnnotationHolder) : EditorConfigVisitor() {
  override fun visitFlatOptionKey(flatKey: EditorConfigFlatOptionKey) {
    holder.createInfoAnnotation(flatKey, null).textAttributes = EditorConfigSyntaxHighlighter.PROPERTY_KEY
  }

  override fun visitQualifiedKeyPart(keyPart: EditorConfigQualifiedKeyPart) {
    val descriptor = keyPart.getDescriptor(false)
    holder.createInfoAnnotation(keyPart, null).textAttributes =
      if (descriptor is EditorConfigDeclarationDescriptor) EditorConfigSyntaxHighlighter.PROPERTY_KEY
      else EditorConfigSyntaxHighlighter.KEY_DESCRIPTION
  }

  override fun visitOptionValueIdentifier(identifier: EditorConfigOptionValueIdentifier) {
    holder.createInfoAnnotation(identifier, null).textAttributes = EditorConfigSyntaxHighlighter.PROPERTY_VALUE
  }

  override fun visitFlatPattern(flatPattern: EditorConfigFlatPattern) {
    holder.createInfoAnnotation(flatPattern, null).textAttributes = EditorConfigSyntaxHighlighter.PATTERN
    if (!flatPattern.textContains('\\')) return

    val text = flatPattern.text
    val offset = flatPattern.textOffset

    var index = 0
    while (index < text.length) {
      if (text[index] == '\\') {
        val range = TextRange(offset + index, offset + index + 2)
        index += 1
        if (EditorConfigSyntaxHighlighter.VALID_ESCAPES.contains(text[index])) {
          holder.createInfoAnnotation(range, null).textAttributes = EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE
        }
        else {
          val message = EditorConfigBundle["annotator.illegal.char.escape"]
          holder.createInfoAnnotation(range, message).textAttributes = EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE
        }
      }
      index += 1
    }
  }

  override fun visitAsteriskPattern(pattern: EditorConfigAsteriskPattern) {
    holder.createInfoAnnotation(pattern, null).textAttributes = EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL
  }

  override fun visitDoubleAsteriskPattern(pattern: EditorConfigDoubleAsteriskPattern) {
    holder.createInfoAnnotation(pattern, null).textAttributes = EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL
  }

  override fun visitCharClassExclamation(exclamation: EditorConfigCharClassExclamation) {
    holder.createInfoAnnotation(exclamation, null).textAttributes = EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL
  }

  override fun visitQuestionPattern(pattern: EditorConfigQuestionPattern) {
    holder.createInfoAnnotation(pattern, null).textAttributes = EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL
  }

  override fun visitCharClassLetter(letter: EditorConfigCharClassLetter) = when {
    !letter.isEscape -> holder.createInfoAnnotation(letter, null).textAttributes = EditorConfigSyntaxHighlighter.PATTERN
    letter.isValidEscape -> holder.createInfoAnnotation(letter, null).textAttributes = EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE
    else -> holder.createInfoAnnotation(letter, EditorConfigBundle["annotator.illegal.char.escape"]).textAttributes =
      EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE
  }

  override fun visitRootDeclarationKey(key: EditorConfigRootDeclarationKey) {
    holder.createInfoAnnotation(key, null).textAttributes = EditorConfigSyntaxHighlighter.PROPERTY_KEY
  }

  override fun visitRootDeclarationValue(value: EditorConfigRootDeclarationValue) {
    holder.createInfoAnnotation(value, null).textAttributes = EditorConfigSyntaxHighlighter.PROPERTY_VALUE
  }
}
