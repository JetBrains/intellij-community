// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

import com.intellij.internal.statistic.eventLog.util.StringUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.util.prefs.Preferences
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent

class JEditorPaneDemoAction: DumbAwareAction("HTML Rendering Playground") {
  private val PREFERECNES_KEY = "HTML_RENDERING_PLAYGROUND"
  private val DEFAULT_HTML = "<html>\n" +
                             "<head>\n" +
                             "<style type=\"text/css\">\n" +
                             "body {margin: 10px; background:#C0C0C0;color:#333333;}\n" +
                             "code {background-color:#eeee11; margin:4px;}\n" +
                             "pre {padding:10px;}\n" +
                             "</style>\n" +
                             "</head>\n" +
                             "<body>\n" +
                             "<h1>This is HTML rendering demo.</h1>\n" +
                             "Some key features are supported:\n" +
                             "<ol><li>Of course <i>Italic</i> & <b>bold</b></li>\n" +
                             "<li>Not so often used <sub>subscript</sub> <strike>strikethrough</strike> and <sup>superscript</sup>\n" +
                             "<li>Tags <small>small</small> and <strong>strong</strong>. And <u>underlined</u> too.</li>\n" +
                             "<li><a href=\"https://www.jetbrains.com/\">External links</a></li>\n" +
                             "<li>This is <code>@Code</code> tag to be <code>highlighted</code></li>\n" +
                             "<li>Emoji etc. if you are lucky enough to see it <span style=\"color:red;\">[&#9829;]</span>[&#128512;]</li>\n" +
                             "</ol>\n" +
                             "The tag below is &lt;pre&gt;:\n" +
                             "<pre style=\"background-color:white;color:black;\">\n" +
                             "class HelloWorld {\n" +
                             "    public static void main(String[] args) {\n" +
                             "        System.out.println(\"Hello World!\");\n" +
                             "    }\n" +
                             "}</pre>\n" +
                             "<ul>\n" +
                             "<li>User-friendly link <a href=\"https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html\">\n" +
                             "CSS support in Java engine</a></li>\n" +
                             "<li>Not so user-friendly link <a href=\"https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html\">\n" +
                             "                             https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/CSS.html</a></li>\n" +
                             "</ul>\n" +
                             "<p>\n" +
                             "<div style=\"color:white;background-color:#909090; border:solid 1px #fedcba; text-align:center;\">Outer div\n" +
                             "<div style=\"background-color:#606060; width:50%; margin:20px;padding:11px; border:solid 1px orange;\">Inner div with width 50%</div>\n" +
                             "</div>\n" +
                             "</p><br>\n" +
                             "<p>\n" +
                             "Well, we can do something with it...</p>\n" +
                             "</body></html>"
  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      val myEditor = JTextArea(40, 80)
      val myView = JEditorPane()
      init {
        title = "HTML Rendering Playground"
        isResizable = true
        myView.contentType = "text/html"
        myView.isEditable = false
        myView.editorKit = UIUtil.getHTMLEditorKit()
        myView.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
        UIUtil.doNotScrollToCaret(myView)
        myEditor.document.addDocumentListener(object: DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            try {
              myView.text = myEditor.text
              Preferences.userRoot().put(PREFERECNES_KEY, myEditor.text)
            }
            catch (ignored: Exception) {
            }
          }
        })
        var html = Preferences.userRoot().get(PREFERECNES_KEY, "")
        if (StringUtil.isEmpty(html)) html = DEFAULT_HTML
        myEditor.text = html
        init()
      }

      override fun getStyle(): DialogStyle {
        return DialogStyle.COMPACT
      }

      override fun getDimensionServiceKey(): String {
        return "HTMLRenderingPlayground"
      }

      override fun createCenterPanel(): JComponent {
        val splitter = OnePixelSplitter(false, .33F, .18F, .82F)
        splitter.splitterProportionKey = "HTMLRenderingPlayground.Splitter"
        splitter.firstComponent = JBScrollPane(myEditor)
        splitter.secondComponent = JBScrollPane(myView)
        return splitter
      }

      override fun createActions() = emptyArray<Action>()

    }.show()
  }
}