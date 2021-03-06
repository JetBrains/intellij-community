// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.jdom.Content
import org.jdom.Element
import org.jdom.Text
import org.jdom.input.SAXBuilder
import org.jdom.output.XMLOutputter
import training.learn.CourseManager
import training.util.KeymapUtil
import training.util.openLinkInBrowser

object MessageFactory {
  private val LOG = Logger.getInstance(MessageFactory::class.java)

  fun setLinksHandlers(project: Project, messageParts: List<MessagePart>) {
    for (message in messageParts) {
      if (message.type == MessagePart.MessageType.LINK && message.runnable == null) {
        //add link handler
        message.runnable = Runnable {
          val link = message.link
          if (link == null || link.isEmpty()) {
            val lesson = CourseManager.instance.findLesson(message.text)
            if (lesson != null) {
              try {
                CourseManager.instance.openLesson(project, lesson)
              }
              catch (e: Exception) {
                LOG.warn(e)
              }

            }
          }
          else {
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
    return text.split("\n").map { paragraph ->
      val wrappedText = "<root><text>$paragraph</text></root>"
      val textAsElement = SAXBuilder().build(wrappedText.byteInputStream()).rootElement.getChild("text")
                          ?: throw IllegalStateException("Can't parse as XML:\n$paragraph")
      convert(textAsElement)
    }.reduce { acc, item -> acc + MessagePart("\n", MessagePart.MessageType.LINE_BREAK) + item }
  }

  private fun convert(element: Element?): List<MessagePart> {
    if (element == null) {
      return emptyList()
    }
    val list: MutableList<MessagePart> = mutableListOf()
    for (content: Content in element.content) {
      if (content is Text) {
        list.add(MessagePart(content.getValue(), MessagePart.MessageType.TEXT_REGULAR))
      }
      else if (content is Element) {
        val outputter = XMLOutputter()
        var type = MessagePart.MessageType.TEXT_REGULAR
        val text: String = StringUtil.unescapeXmlEntities(outputter.outputString(content.content))
        var textFn = { text }
        var link: String? = null
        var runnable: Runnable? = null
        when (content.name) {
          "icon" -> error("Need to return reflection-based icon processing")
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
            textFn = {
              val shortcutByActionId = KeymapUtil.getShortcutByActionId(text)
              if (shortcutByActionId != null) {
                KeymapUtil.getKeyStrokeText(shortcutByActionId)
              }
              else {
                KeymapUtil.getGotoActionText(text)
              }
            }
          }
          "raw_action" -> {
            type = MessagePart.MessageType.SHORTCUT
          }
          "ide" -> {
            type = MessagePart.MessageType.TEXT_REGULAR
            textFn = { ApplicationNamesInfo.getInstance().fullProductName }
          }
        }
        val message = MessagePart(type, textFn)
        message.link = link
        message.runnable = runnable
        list.add(message)
      }
    }
    return list
  }
}