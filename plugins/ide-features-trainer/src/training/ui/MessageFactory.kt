// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.*
import com.intellij.ide.ui.text.showActionKeyPopup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.jdom.Text
import org.jdom.output.XMLOutputter
import training.dsl.LessonUtil
import training.learn.LearnBundle
import training.statistic.StatisticBase
import training.util.invokeActionForFocusContext
import training.util.openLinkInBrowser
import java.awt.Point
import java.util.regex.Pattern
import javax.swing.JTextPane

internal object MessageFactory {
  private val LOG = Logger.getInstance(MessageFactory::class.java)

  fun convert(@Language("HTML") text: String): List<TextParagraph> {
    return text.splitToSequence("\n")
      .map { paragraph ->
        val wrappedText = "<root><text>$paragraph</text></root>"
        val textAsElement = JDOMUtil.load(wrappedText.byteInputStream()).getChild("text")
                            ?: throw IllegalStateException("Can't parse as XML:\n$paragraph")
        val parts = convert(textAsElement)
        TextParagraph(parts)
      }
      .toList()
  }

  private fun convert(element: Element): List<TextPart> {
    val list = mutableListOf<TextPart>()
    for (content in element.content) {
      if (content is Text) {
        var text = content.getValue()
        if (Pattern.matches(" *\\p{IsPunctuation}.*", text)) {
          val indexOfFirst = text.indexOfFirst { it != ' ' }
          text = StringUtil.NON_BREAK_SPACE.repeat(indexOfFirst) + text.substring(indexOfFirst)
        }
        list.add(RegularTextPart(text))
      }
      else if (content is Element) {
        val outputter = XMLOutputter()
        val text: String = Strings.unescapeXmlEntities(outputter.outputString(content.content))

        val newPart: TextPart = when (content.name) {
          "icon" -> error("Need to return reflection-based icon processing")
          "icon_idx" -> {
            val icon = LearningUiManager.iconMap[text] ?: error("Not found icon with id: $text")
            IconTextPart(icon)
          }
          "illustration" -> {
            val icon = LearningUiManager.iconMap[text] ?: error("Not found icon with id: $text")
            IllustrationTextPart(icon)
          }
          "strong" -> RegularTextPart(text, isBold = true)
          "code" -> CodeTextPart(text)
          "callback" -> {
            val id = content.getAttributeValue("id")
            if (id != null) {
              val callback = LearningUiManager.getAndClearCallback(id.toInt())
              if (callback != null) {
                LinkTextPart(text, callback)
              }
              else error("Unknown callback with id '$id' and text '$text'")
            }
            else error("'callback' tag with text '$text' should contain 'id' attribute")
          }
          "a" -> {
            val link = content.getAttributeValue("href")
                       ?: error("'a' tag with text '$text' should contain 'href' attribute")
            LinkTextPart(text) {
              try {
                openLinkInBrowser(link)
              }
              catch (ex: Exception) {
                LOG.warn(ex)
              }
            }
          }
          "action" -> IftShortcutTextPart(text, isRaw = false)
          "shortcut" -> IftShortcutTextPart(text, isRaw = true)
          "raw_shortcut" -> IftShortcutTextPart(text, isRaw = true)
          "ide" -> RegularTextPart(LessonUtil.productName)
          else -> error("Unknown tag: ${content.name}")
        }
        list.add(newPart)
      }
    }
    return list
  }

  private class IftShortcutTextPart(text: String, isRaw: Boolean) : ShortcutTextPart(text, isRaw) {
    override val onClickAction: ((JTextPane, Point, height: Int) -> Unit)?
      get() {
        return if (!isRaw) {
          { textPane: JTextPane, point: Point, height: Int ->
            val actionId = text
            showActionKeyPopupExtended(textPane, point, height, actionId)
            StatisticBase.logShortcutClicked(actionId)
          }
        }
        else null
      }

    private fun showActionKeyPopupExtended(textPane: JTextPane, point: Point, height: Int, actionId: String) {
      showActionKeyPopup(textPane, point, height, actionId) { panel ->
        panel.add(ActionLink(LearnBundle.message("shortcut.balloon.apply.this.action")) {
          val action = ActionManager.getInstance().getAction(actionId)
          invokeActionForFocusContext(action)
        }.also {
          it.foreground = JBColor.namedColor("ToolTip.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)
        })
      }
    }
  }
}