// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

internal class MessagePart(val type: MessageType, private val textFn: () -> String) {

  constructor(text: String, type: MessageType): this(type, {text})

  val text: String
    get() = textFn()

  enum class MessageType { TEXT_REGULAR, TEXT_BOLD, SHORTCUT, CODE, LINK, CHECK, ICON_IDX, PROPOSE_RESTORE, LINE_BREAK }

  var startOffset = 0
  var endOffset = 0
  var link: String? = null
  var runnable: Runnable? = null

  var splitter: () -> List<IntRange> = {
    listOf(IntRange(0, endOffset - startOffset - 1))
  }

  override fun toString(): String {
    return "Message{" +
           "messageText='" + text + '\''.toString() +
           ", messageType=" + type +
           '}'
  }

  fun splitMe(): List<MessagePart> {
    return splitter().map {
      MessagePart(text.substring(it), type).also { part->
        part.startOffset = startOffset + it.first
        part.endOffset = startOffset + it.last + 1
        part.link = link
        part.runnable = runnable
      }
    }
  }
}