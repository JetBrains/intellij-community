/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting.engine.testModel

import com.intellij.formatting.Block
import com.intellij.formatting.FormattingDocumentModel
import com.intellij.formatting.FormattingModel
import com.intellij.lang.ASTNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil

class TestFormattingModel(private val rootBlock: Block,
                          private val document: Document) : FormattingModel, FormattingDocumentModel {

  override fun getRootBlock(): Block = rootBlock

  override fun getDocumentModel(): TestFormattingModel = this

  override fun replaceWhiteSpace(textRange: TextRange, whiteSpace: String): TextRange {
    WriteCommandAction.runWriteCommandAction(null) {
      document.replaceString(textRange.startOffset, textRange.endOffset, whiteSpace)
    }
    return TextRange(textRange.startOffset, textRange.startOffset + whiteSpace.length)
  }

  override fun shiftIndentInsideRange(node: ASTNode, range: TextRange, indent: Int): TextRange {
    throw UnsupportedOperationException()
  }

  override fun commitChanges() {
  }

  override fun getLineNumber(offset: Int): Int = document.getLineNumber(offset)

  override fun getLineStartOffset(line: Int): Int = document.getLineStartOffset(line)

  override fun getText(textRange: TextRange): String = document.getText(textRange)

  override fun getTextLength(): Int = document.textLength

  override fun getDocument(): Document = document

  override fun containsWhiteSpaceSymbolsOnly(startOffset: Int, endOffset: Int): Boolean {
    return CharArrayUtil.containsOnlyWhiteSpaces(document.getText(TextRange(startOffset, endOffset)))
  }

  override fun adjustWhiteSpaceIfNecessary(whiteSpaceText: CharSequence,
                                           startOffset: Int,
                                           endOffset: Int,
                                           nodeAfter: ASTNode?,
                                           changedViaPsi: Boolean): CharSequence {
    throw UnsupportedOperationException("not implemented")
  }
}