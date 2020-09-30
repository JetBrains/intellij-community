// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component

import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.utils.GrazieLinkLabel
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.ComparableCategory
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.RuleWithLang
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.*
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.html.*
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NotNull
import org.languagetool.rules.IncorrectExample
import org.languagetool.rules.Rule
import java.awt.BorderLayout
import javax.swing.JEditorPane
import javax.swing.ScrollPaneConstants

class GrazieDescriptionComponent : GrazieUIComponent.ViewOnly {
  companion object {
    private const val MINIMUM_EXAMPLES_SIMILARITY = 0.2

    private fun CharSequence.isSimilarTo(sequence: CharSequence): Boolean {
      return Text.Levenshtein.distance(this, sequence).toDouble() / length < MINIMUM_EXAMPLES_SIMILARITY
    }
  }

  private val description = JEditorPane().apply {
    editorKit = UIUtil.getHTMLEditorKit()
    isEditable = false
    isOpaque = true
    border = null
    background = null
  }
  private val link = GrazieLinkLabel(msg("grazie.settings.grammar.rule.description")).apply {
    component.isVisible = false
  }

  val listener: (Any) -> Unit
    get() = { selection ->
      link.component.isVisible = if (selection is RuleWithLang && selection.rule.url != null) {
        link.listener = LinkListener { _: @NotNull LinkLabel<Any?>, _: Any? -> BrowserUtil.browse(selection.rule.url!!) }
        true
      }
      else false

      @NlsSafe val context = getDescriptionPaneContent(selection).also {
        description.isVisible = it.isNotBlank()
      }
      description.text = context
    }

  override val component = panel(MigLayout(createLayoutConstraints().flowY().fillX().gridGapY("7"))) {
    border = padding(JBUI.insets(30, 20, 0, 0))
    add(link.component, CC().grow().hideMode(3))

    val descriptionPanel = JBPanelWithEmptyText(BorderLayout(0, 0)).withEmptyText(msg("grazie.settings.grammar.rule.no-description"))
    descriptionPanel.add(description)
    add(ScrollPaneFactory.createScrollPane(descriptionPanel, SideBorder.NONE).also {
      it.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }, CC().grow().push())
  }

  private fun hasDescription(rule: Rule) = rule.url != null
                                           || rule.incorrectExamples?.isNotEmpty().orFalse()
                                           || LangTool.getRuleLanguages(rule.id)?.let { it.size > 1 }.orFalse()

  @NlsSafe
  private fun getDescriptionPaneContent(it: Any): String {
    return when {
      it is Lang -> html {
        unsafe { +msg("grazie.settings.grammar.rule.language.template", it.nativeName) }
      }
      it is ComparableCategory -> html {
        unsafe { +msg("grazie.settings.grammar.rule.category.template", it.category.getName()) }
      }
      it is RuleWithLang && hasDescription(it.rule) -> {
        html {
          table {
            cellpading = "0"
            cellspacing = "0"
            style = "width:100%;"

            LangTool.getRuleLanguages(it.rule.id)?.let { languages ->
              if (languages.size > 1) {
                tr {
                  td {
                    colSpan = "2"
                    style = "padding-bottom: 10px;"

                    +msg("grazie.settings.grammar.rule.multi-language.start")
                    +" "
                    strong {
                      +languages.first().nativeName
                      languages.drop(1).forEach {
                        +", ${it.nativeName}"
                      }
                    }
                    +" "
                    +msg("grazie.settings.grammar.rule.multi-language.end")
                  }
                }
              }
            }

            it.rule.incorrectExamples?.let { examples ->
              if (examples.isNotEmpty()) {
                val accepted = ArrayList<IncorrectExample>()
                // remove very similar examples
                examples.forEach { example ->
                  if (accepted.none { it.text.isSimilarTo(example.text) }) {
                    accepted.add(example)
                  }
                }

                accepted.forEach { example ->
                  tr {
                    td {
                      valign = "top"
                      style = "padding-bottom: 5px; padding-right: 5px; color: gray;"
                      +msg("grazie.settings.grammar.rule.incorrect")
                    }
                    td {
                      style = "padding-bottom: 5px; width: 100%;"
                      toIncorrectHtml(example)
                    }
                  }

                  if (example.corrections.any { it.isNotBlank() }) {
                    tr {
                      td {
                        valign = "top"
                        style = "padding-bottom: 10px; padding-right: 5px; color: gray;"
                        +msg("grazie.settings.grammar.rule.correct")
                      }
                      td {
                        style = "padding-bottom: 10px; width: 100%;"
                        toCorrectHtml(example)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      else -> ""
    }
  }
}
