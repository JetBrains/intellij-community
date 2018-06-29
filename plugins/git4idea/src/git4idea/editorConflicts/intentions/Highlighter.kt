// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange

class Highlighter(private val myEditor: Editor) {
  private val myActiveHighlighters: MutableList<RangeHighlighter> = mutableListOf()

  fun highlight(range: List<TextRange>) {
    dropHighlight()

    addHighlighter(range)
  }

  private fun addHighlighter(ranges: List<TextRange>) {
    for (r in ranges) {
      myActiveHighlighters.add(myEditor.markupModel.addRangeHighlighter(r.startOffset, r.endOffset,
                                                                        HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                        getAttributes(),
                                                                        HighlighterTargetArea.EXACT_RANGE))
    }
  }

  private fun getAttributes(): TextAttributes? {
    val manager = EditorColorsManager.getInstance()
    return manager.globalScheme.getAttributes(EditorColors.DELETED_TEXT_ATTRIBUTES)
  }

  fun dropHighlight() {
    myActiveHighlighters.forEach { it.dispose() }
    myActiveHighlighters.clear()
  }
}