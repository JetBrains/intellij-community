// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.settings

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.pane
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.components.rules.ComparableCategory
import com.intellij.grazie.ide.ui.components.rules.RuleWithLang
import com.intellij.grazie.ide.ui.components.utils.GrazieLinkLabel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.*
import com.intellij.ide.BrowserUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import kotlinx.html.*
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.languagetool.rules.IncorrectExample
import org.languagetool.rules.Rule
import java.awt.BorderLayout
import javax.swing.ScrollPaneConstants

class GrazieRuleDescriptionComponent {
  companion object {
    private const val MINIMUM_EXAMPLES_SIMILARITY = 0.2

    private fun CharSequence.isSimilarTo(sequence: CharSequence): Boolean {
      return Text.Levenshtein.distance(this, sequence).toDouble() / length < MINIMUM_EXAMPLES_SIMILARITY
    }
  }

  private val description = pane()
  private val link = GrazieLinkLabel(msg("grazie.ui.settings.rules.rule.description")).apply {
    component.name = "GRAZIE_LINK_PANEL"
    component.isVisible = false
  }

  val listener: (Any) -> Unit
    get() = { selection ->
      link.component.isVisible = if (selection is RuleWithLang && selection.rule.url != null) {
        link.listener = LinkListener { _: Any?, _: Any? -> BrowserUtil.browse(selection.rule.url!!) }
        true
      }
      else false

      description.text = getDescriptionPaneContent(selection).also {
        description.isVisible = it.isNotBlank()
      }
    }

  val component = panel(MigLayout(createLayoutConstraints().flowY().fillX().gridGapY("7"))) {
    border = padding(JBUI.insets(30, 20, 0, 0))
    add(link.component, CC().grow().hideMode(3))

    val descriptionPanel = JBPanelWithEmptyText(BorderLayout(0, 0)).withEmptyText(msg("grazie.ui.settings.rules.no-description"))
    descriptionPanel.add(description)
    add(ScrollPaneFactory.createScrollPane(descriptionPanel, SideBorder.NONE).also {
      it.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }, CC().grow().push())
  }

  private fun hasDescription(rule: Rule) = rule.url != null || rule.incorrectExamples?.isNotEmpty().orFalse() || LangTool.getRuleLanguages(
    rule.id)?.let { it.size > 1 }.orFalse()

  private fun getDescriptionPaneContent(it: Any): String {
    return when {
      it is Lang -> html {
        unsafe { +msg("grazie.ui.settings.rules.language.template", it.displayName) }
      }
      it is ComparableCategory -> html {
        unsafe { +msg("grazie.ui.settings.rules.category.template", it.category.getName()) }
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

                    +msg("grazie.ui.settings.rules.rule.multilanguage.start")
                    +" "
                    strong {
                      +languages.first().displayName
                      languages.drop(1).forEach {
                        +", ${it.displayName}"
                      }
                    }
                    +" "
                    +msg("grazie.ui.settings.rules.rule.multilanguage.end")
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
                      +msg("grazie.ui.settings.rules.rule.incorrect")
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
                        +msg("grazie.ui.settings.rules.rule.correct")
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
