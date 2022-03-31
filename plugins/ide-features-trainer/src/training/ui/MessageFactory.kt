// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.intellij.lang.annotations.Language
import training.dsl.LessonUtil
import training.util.KeymapUtil
import training.util.openLinkInBrowser
import java.util.regex.Pattern
import javax.swing.KeyStroke

internal object MessageFactory {
  private val LOG = Logger.getInstance(MessageFactory::class.java)

  fun setLinksHandlers(messageParts: List<MessagePart>) {
    for (message in messageParts) {
      if (message.type == MessagePart.MessageType.LINK && message.runnable == null) {
        val link = message.link
        if (link.isNullOrEmpty()) {
          LOG.error("No link specified for ${message.text}")
        }
        else {
          message.runnable = Runnable {
            try {
              openLinkInBrowser(link)
            }
            catch (e: Exception) {
              LOG.warn(e)
            }
          }
        }
      }
    }
  }


  fun convert(@Language("HTML") text: String): List<MessagePart> {
    return text
      .splitToSequence("\n")
      .map { paragraph ->
        val wrappedText = "<root><text>$paragraph</text></root>"
        val textAsElement = readXmlAsModel(wrappedText.reader()).getChild("text")
                            ?: throw IllegalStateException("Can't parse as XML:\n$paragraph")
        convert(textAsElement)
      }
      .reduce { acc, item -> acc + MessagePart("\n", MessagePart.MessageType.LINE_BREAK) + item }
  }

  private fun convert(element: XmlElement?): List<MessagePart> {
    if (element == null) {
      return emptyList()
    }

    val list = mutableListOf<MessagePart>()

    element.content?.let { text ->
      var t = text
      if (Pattern.matches(" *\\p{IsPunctuation}.*", t)) {
        val indexOfFirst = text.indexOfFirst { it != ' ' }
        t = "\u00A0".repeat(indexOfFirst) + t.substring(indexOfFirst)
      }
      list.add(MessagePart(t, MessagePart.MessageType.TEXT_REGULAR))
    }

    for (content in element.children) {
      var type = MessagePart.MessageType.TEXT_REGULAR
      val text = content.content ?: ""
      var textAndSplitFn: (() -> Pair<String, List<IntRange>?>)? = null
      var link: String? = null
      var runnable: Runnable? = null
      when (content.name) {
        "icon" -> error("Need to return reflection-based icon processing")
        "illustration" -> type = MessagePart.MessageType.ILLUSTRATION
        "icon_idx" -> type = MessagePart.MessageType.ICON_IDX
        "code" -> type = MessagePart.MessageType.CODE
        "shortcut" -> type = MessagePart.MessageType.SHORTCUT
        "strong" -> type = MessagePart.MessageType.TEXT_BOLD
        "callback" -> {
          type = MessagePart.MessageType.LINK
          val id = content.getAttributeValue("id")
          if (id != null) {
            val callback = LearningUiManager.getAndClearCallback(id.toInt())
            if (callback != null) {
              runnable = Runnable { callback() }
            }
            else {
              LOG.error("Unknown callback with id $id and text $text")
            }
          }
        }

        "a" -> {
          type = MessagePart.MessageType.LINK
          link = content.getAttributeValue("href")
        }

        "action" -> {
          type = MessagePart.MessageType.SHORTCUT
          link = text
          textAndSplitFn = {
            val shortcutByActionId = KeymapUtil.getShortcutByActionId(text)
            if (shortcutByActionId != null) {
              KeymapUtil.getKeyStrokeData(shortcutByActionId)
            }
            else {
              KeymapUtil.getGotoActionData(text)
            }
          }
        }

        "raw_shortcut" -> {
          type = MessagePart.MessageType.SHORTCUT
          textAndSplitFn = {
            KeymapUtil.getKeyStrokeData(KeyStroke.getKeyStroke(text))
          }
        }

        "ide" -> {
          type = MessagePart.MessageType.TEXT_REGULAR
          textAndSplitFn = { LessonUtil.productName to null }
        }
      }

      val message = MessagePart(type, textAndSplitFn ?: { text to null })
      message.link = link
      message.runnable = runnable
      list.add(message)
    }
    return list
  }
}