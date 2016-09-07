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
package com.intellij.ui

import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.util.regex.Pattern
import javax.swing.JEditorPane

private val HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>")
private val LINK_TEXT_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.blue)
private val SMALL_TEXT_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null)

fun noteComponent(note: String): SimpleColoredComponent {
  val noteComponent = SimpleColoredComponent()

  val matcher = HREF_PATTERN.matcher(note)
  var prev = 0
  if (matcher.find()) {
    do {
      if (matcher.start() != prev) {
        noteComponent.append(note.substring(prev, matcher.start()), SMALL_TEXT_ATTRIBUTES)
      }
      noteComponent.append(matcher.group(2), LINK_TEXT_ATTRIBUTES, SimpleColoredComponent.BrowserLauncherTag(matcher.group(1)))
      prev = matcher.end()
    }
    while (matcher.find())

    LinkMouseListenerBase.installSingleTagOn(noteComponent)
  }

  if (prev < note.length) {
    noteComponent.append(note.substring(prev), SMALL_TEXT_ATTRIBUTES)
  }

  return noteComponent
}

@JvmOverloads
fun htmlComponent(text: String = "", font: Font = UIUtil.getLabelFont(), background: Color? = null, foreground: Color? = null, lineWrap: Boolean = false): JEditorPane {
  val pane = SwingHelper.createHtmlViewer(lineWrap, font, background, foreground)
  if (!text.isNullOrEmpty()) {
    pane.text = "<html><head>${UIUtil.getCssFontDeclaration(font, UIUtil.getLabelForeground(), null, null)}</head><body>$text</body></html>"
  }
  pane.border = null
  pane.disabledTextColor = UIUtil.getLabelDisabledForeground()
  pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
  return pane
}