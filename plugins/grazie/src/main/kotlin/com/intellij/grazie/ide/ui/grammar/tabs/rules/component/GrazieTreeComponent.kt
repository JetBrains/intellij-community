// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeCellRenderer
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeFilter
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeNode
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.utils.getAffectedGlobalRules
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SearchTextField
import com.intellij.ui.TreeSpeedSearch
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.tree.DefaultTreeModel

class GrazieTreeComponent(
  onSelectionChanged: (meta: Any) -> Unit,
  private val language: Language,
  private val domain: TextStyleDomain,
  private val filter: SearchTextField,
) : CheckboxTree(GrazieRulesTreeCellRenderer(), GrazieRulesTreeNode(domain)),
    Disposable, GrazieUIComponent {
  private val disabledRules = hashSetOf<String>()
  private val enabledRules = hashSetOf<String>()
  private val filterComponent = GrazieRulesTreeFilter(this, language)

  init {
    selectionModel.addTreeSelectionListener { event ->
      val meta = TreeUtil.getLastUserObject(event?.path)
      if (meta != null) onSelectionChanged(meta)
    }

    addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val meta = node.userObject
        if (meta is Rule) {
          val id = meta.globalId
          enabledRules.remove(id)
          disabledRules.remove(id)
          if (node.isChecked != meta.isEnabledByDefault(domain)) {
            (if (node.isChecked) enabledRules else disabledRules).add(id)
          }
        }
      }
    })
  }

  override fun installSpeedSearch() {
    TreeSpeedSearch.installOn(this, false) { (it.lastPathComponent as GrazieRulesTreeNode).nodeText }
  }

  override val component by lazy {
    panel tree@{
      panel(constraint = BorderLayout.NORTH) {
        border = JBUI.Borders.emptyBottom(2)

        DefaultActionGroup().apply {
          val actionManager = CommonActionsManager.getInstance()
          val treeExpander = DefaultTreeExpander(this@GrazieTreeComponent)
          add(actionManager.createExpandAllAction(treeExpander, this@GrazieTreeComponent))
          add(actionManager.createCollapseAllAction(treeExpander, this@GrazieTreeComponent))

          val toolbar = ActionManager.getInstance().createActionToolbar("GrazieRulesTab", this, true)
          toolbar.setTargetComponent(this@tree)
          add(toolbar.component, BorderLayout.WEST)
        }
      }
    }
  }

  override fun isModified(state: GrazieConfig.State): Boolean {
    val (userEnabledRules, userDisabledRules) = state.getUserChangedRules(domain)
    return filterByLanguage(userEnabledRules) != enabledRules || filterByLanguage(userDisabledRules) != disabledRules
  }

  override fun reset(state: GrazieConfig.State) {
    val (userEnabledRules, userDisabledRules) = state.getUserChangedRules(domain)
    enabledRules.clear(); enabledRules.addAll(filterByLanguage(userEnabledRules))
    disabledRules.clear(); disabledRules.addAll(filterByLanguage(userDisabledRules))
    filterComponent.filter(filter.text)
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State {
    return state.updateUserRules(domain, enabledRules, disabledRules)
  }

  override fun dispose() {}

  private fun filterByLanguage(rules: Set<String>): Set<String> =
    rules.filter { it.contains(".${language.iso.name}.", ignoreCase = true) }.toSet()

  @JvmOverloads
  fun filter(filterText: String? = filter.text) {
    filterComponent.filter(filterText)
  }

  fun getCurrentFilterString(): String? = filter.text

  fun focusRule(rule: Rule) {
    val ruleNode = (model.root as GrazieRulesTreeNode).findRuleNode(rule.globalId)
    if (ruleNode != null) {
      TreeUtil.selectNode(this, ruleNode)
    }
  }

  fun resetTreeModel(rules: List<Rule>) {
    val root = GrazieRulesTreeNode(domain)
    val model = model as DefaultTreeModel

    fun splitIntoCategories(level: Int, rules: List<Rule>, parent: GrazieRulesTreeNode) {
      rules.groupBy { it.categories.getOrNull(level) }.entries
        .sortedWith(Comparator.comparing({ it.key }, nullsLast(Comparator.comparing { it.lowercase() })))
        .forEach { (category, categoryRules) ->
          if (category != null) {
            val categoryNode = GrazieRulesTreeNode(domain, category)
            model.insertNodeInto(categoryNode, parent, parent.childCount)
            splitIntoCategories(level + 1, categoryRules, categoryNode)
          }
          else {
            categoryRules.sortedBy { it.presentableName.lowercase() }.forEach { rule ->
              model.insertNodeInto(GrazieRulesTreeNode(domain, rule), parent, parent.childCount)
            }
          }
        }
    }

    val affectedGlobalRules = getAffectedGlobalRules(language)
    splitIntoCategories(
      0,
      rules.filter { it.globalId !in affectedGlobalRules },
      root
    )

    val state = GrazieConfig.get()
    model.setRoot(root)
    root.resetMark(apply(state))
    model.nodeChanged(root)
  }
}

@ApiStatus.Internal
fun allRules(state: GrazieConfig.State = GrazieConfig.get()): Map<Lang, List<Rule>> {
  val result = hashMapOf<Lang, List<Rule>>()
  state.enabledLanguages.forEach { lang ->
    val jLanguage = lang.jLanguage
    if (jLanguage != null) {
      val rules = TextChecker.allCheckers().flatMap { it.getRules(jLanguage.localeWithCountryAndVariant) }
      if (rules.isNotEmpty()) {
        result[lang] = rules
      }
    }
  }
  return result
}

@ApiStatus.Internal
fun allRules(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): List<Rule> {
  if (lang !in state.enabledLanguages) return emptyList()
  val jLanguage = lang.jLanguage
  if (jLanguage != null) {
    val rules = TextChecker.allCheckers().flatMap { it.getRules(jLanguage.localeWithCountryAndVariant) }
    if (rules.isNotEmpty()) {
      return rules
    }
  }
  return emptyList()
}
