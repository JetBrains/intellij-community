// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.editor.Editor

data class LessonSamplePosition(val id: Int, val startOffset: Int, val selection: Pair<Int, Int>? = null)

class LessonSample(val text: String,
                   private val positions: Map<Int, LessonSamplePosition>) {
  constructor(text: String, position: LessonSamplePosition) :
    this(text, mapOf(Pair(0, LessonSamplePosition(0, position.startOffset, position.selection))))
  constructor(text: String, startOffset: Int) : this(text, LessonSamplePosition(0, startOffset))

  val startOffset: Int
    get() = getPosition(0).startOffset
  val selection: Pair<Int, Int>?
    get() = getPosition(0).selection

  fun getPosition(id: Int): LessonSamplePosition {
    return positions[id] ?: if (id == 0) LessonSamplePosition(0, 0) else error("No id $id")
  }
}

fun createFromTemplate(template: LessonSample, insert: String): LessonSample {
  return LessonSample(LessonUtil.insertIntoSample(template, insert),
                      mapOf(Pair(0, LessonSamplePosition(0, template.startOffset, template.selection))))
}

fun parseLessonSample(rowText: String): LessonSample {
  val text = if (rowText.isEmpty() || rowText.last() == '\n') rowText else rowText + '\n'
  val positionList = mutableListOf<LessonSamplePosition>()
  val resultText = parseCarets(text, positionList)
  val positions = mutableMapOf<Int, LessonSamplePosition>()
  if (positionList.isNotEmpty()) {
    for (position in positionList) {
      if (positions[position.id] != null) error("several same id in the sample")
      positions[position.id] = position
    }
  }
  else {
    positions[0] = LessonSamplePosition(id = 0, startOffset = 0)
  }
  return LessonSample(resultText, positions)
}

private fun parseCarets(text: String, positions: MutableList<LessonSamplePosition>): String {
  val result = StringBuilder()

  var idx = 0
  fun skipSpace() {
    while (idx < text.length && text[idx] == ' ') idx++
  }

  fun equalSymbol(c: Char) = idx < text.length && text[idx] == c

  fun acceptPrefix(prefix: String): Boolean {
    if (text.subSequence(idx, text.length).startsWith(prefix)) {
      idx += prefix.length
      return true
    }
    return false
  }

  fun parseId(suffix: String): Int? {
    skipSpace()
    if (!acceptPrefix("id")) return null
    skipSpace()
    if (!acceptPrefix("=")) return null
    skipSpace()
    val end = text.indexOf(suffix, idx)
    if (end == -1) return null
    val id = Integer.parseInt(text.subSequence(idx, end).toString())
    idx = end + suffix.length
    return id
  }

  val selectionEnd = "</select>"

  var previousEndIdx = 0
  while (idx >= 0 && idx < text.length) {
    idx = text.indexOf('<', idx)
    var startIdx = idx

    fun appendText() {
      result.append(text.subSequence(previousEndIdx, startIdx))
      previousEndIdx = idx
    }

    fun parseSelection(): Int? {
      appendText()
      val startSelection = result.length
      idx = text.indexOf(selectionEnd, idx)
      if (idx == -1) return null
      startIdx = idx
      idx += selectionEnd.length
      appendText()
      return startSelection
    }

    if (idx < 0) break
    idx++
    if (acceptPrefix("caret")) {
      if (acceptPrefix(">")) {
        appendText()
        positions.add(LessonSamplePosition(id = 0, startOffset = result.length))
      }
      else if (equalSymbol(' ')) {
        val id = parseId("/>") ?: continue
        appendText()
        positions.add(LessonSamplePosition(id = id, startOffset = result.length))
      }
    }
    else if (acceptPrefix("select")) {
      if (acceptPrefix(">")) {
        val startSelection = parseSelection() ?: continue
        positions.add(LessonSamplePosition(id = 0, startOffset = result.length, selection = Pair(startSelection, result.length)))
      }
      else if (equalSymbol(' ')) {
        val id = parseId(">") ?: continue
        val startSelection = parseSelection() ?: continue
        positions.add(LessonSamplePosition(id = id, startOffset = result.length, selection = Pair(startSelection, result.length)))
      }
    }
  }
  result.append(text.subSequence(previousEndIdx, text.length))
  return result.toString()
}

fun prepareSampleFromCurrentState(editor: Editor): LessonSample {
  val text = editor.document.text
  val currentCaret = editor.caretModel.currentCaret
  val position =
    if (currentCaret.hasSelection()) LessonSamplePosition(id = 0, startOffset = currentCaret.offset, selection = Pair(currentCaret.selectionStart, currentCaret.selectionEnd))
    else LessonSamplePosition(id = 0, startOffset = currentCaret.offset)
  return LessonSample(text, mapOf(Pair(0, position)))
}