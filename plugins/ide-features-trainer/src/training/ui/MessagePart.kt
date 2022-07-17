// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

internal class MessagePart(val type: MessageType, private val textAndSplitFn: () -> Pair<String, List<IntRange>?>) {

  constructor(text: String, type: MessageType) : this(type, { text to null })

  lateinit var text: String
  var split: List<IntRange>? = null

  enum class MessageType { TEXT_REGULAR, TEXT_BOLD, SHORTCUT, CODE, LINK, CHECK, ICON_IDX, PROPOSE_RESTORE, LINE_BREAK, ILLUSTRATION }

  var startOffset = 0
  var endOffset = 0
  var link: String? = null
  var runnable: Runnable? = null

  override fun toString(): String {
    return "Message{" +
           "messageText='" + text + '\''.toString() +
           ", messageType=" + type +
           '}'
  }

  fun updateTextAndSplit() {
    textAndSplitFn().let {
      text = it.first
      split = it.second
    }
  }

  fun splitMe(): List<MessagePart> {
    val split1 = split ?: listOf(IntRange(0, endOffset - startOffset - 1))
    return split1.map {
      MessagePart(text.substring(it), type).also { part ->
        part.startOffset = startOffset + it.first
        part.endOffset = startOffset + it.last + 1
        part.link = link
        part.runnable = runnable
      }
    }
  }
}