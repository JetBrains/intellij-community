// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.utils.GrazieLinkLabel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.Rule
import com.intellij.grazie.utils.html
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.html.unsafe
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.JEditorPane
import javax.swing.ScrollPaneConstants

class GrazieDescriptionComponent {
  private val description = JEditorPane().apply {
    editorKit = UIUtil.getHTMLEditorKit()
    isEditable = false
    isOpaque = true
    border = null
    background = null
    addHyperlinkListener(BrowserHyperlinkListener())
  }
  private val link = GrazieLinkLabel(msg("grazie.settings.grammar.rule.description")).apply {
    component.isVisible = false
  }

  val listener: (Any) -> Unit
    get() = { selection ->
      val url = if (selection is Rule) selection.url else null
      if (url != null) {
        link.listener = LinkListener { _: @NotNull LinkLabel<Any?>, _: Any? -> BrowserUtil.browse(url) }
      }
      link.component.isVisible = url != null

      val content = getDescriptionPaneContent(selection)
      description.text = content
      description.isVisible = content.isNotBlank()
    }

  val component = panel(MigLayout(createLayoutConstraints().flowY().fillX().gridGapY("7"))) {
    border = padding(JBUI.insets(30, 20, 0, 0))
    add(link.component, CC().grow().hideMode(3))

    val descriptionPanel = JBPanelWithEmptyText(BorderLayout(0, 0)).withEmptyText(msg("grazie.settings.grammar.rule.no-description"))
    descriptionPanel.add(description)
    add(ScrollPaneFactory.createScrollPane(descriptionPanel, SideBorder.NONE).also {
      it.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }, CC().grow().push())
  }

  @NlsSafe
  private fun getDescriptionPaneContent(meta: Any): String = when (meta) {
    is Lang -> html { unsafe { +msg("grazie.settings.grammar.rule.language.template", meta.nativeName) } }
    is String -> html { unsafe { +msg("grazie.settings.grammar.rule.category.template", meta) } }
    is Rule -> meta.description
    else -> ""
  }
}
