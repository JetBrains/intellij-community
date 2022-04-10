// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeCellRenderer
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeFilter
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.GrazieRulesTreeNode
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextChecker
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultTreeModel

internal class GrazieTreeComponent(onSelectionChanged: (meta: Any) -> Unit) : CheckboxTree(GrazieRulesTreeCellRenderer(), GrazieRulesTreeNode()),
                                                                              Disposable, GrazieUIComponent {
  private val disabledRules = hashSetOf<String>()
  private val enabledRules = hashSetOf<String>()
  private val filterComponent: GrazieRulesTreeFilter = GrazieRulesTreeFilter(this)

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
          if (node.isChecked != meta.isEnabledByDefault) {
            (if (node.isChecked) enabledRules else disabledRules).add(id)
          }
        }
      }
    })
  }

  override fun installSpeedSearch() {
    TreeSpeedSearch(this) { (it.lastPathComponent as GrazieRulesTreeNode).nodeText }
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

        add(filterComponent, BorderLayout.CENTER)
      }

      panel(constraint = BorderLayout.CENTER) {
        add(ScrollPaneFactory.createScrollPane(this@GrazieTreeComponent,
                                               ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER))
      }
    }
  }

  override fun isModified(state: GrazieConfig.State): Boolean {
    return state.userEnabledRules != enabledRules || state.userDisabledRules != disabledRules
  }

  override fun reset(state: GrazieConfig.State) {
    enabledRules.clear(); enabledRules.addAll(state.userEnabledRules)
    disabledRules.clear(); disabledRules.addAll(state.userDisabledRules)
    filterComponent.filter()
    if (isSelectionEmpty) setSelectionRow(0)
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State {
    return state.copy(
      userEnabledRules = HashSet(enabledRules),
      userDisabledRules = HashSet(disabledRules)
    )
  }

  override fun dispose() {
    filterComponent.dispose()
  }

  fun filter(str: String) {
    filterComponent.filter = str
    filterComponent.filter()
  }

  fun getCurrentFilterString(): String? = filterComponent.filter

  fun resetTreeModel(rules: Map<Lang, List<Rule>>) {
    val root = GrazieRulesTreeNode()
    val model = model as DefaultTreeModel

    rules.entries.sortedBy { it.key.nativeName }.forEach { (lang, rules) ->
      val langNode = GrazieRulesTreeNode(lang)
      model.insertNodeInto(langNode, root, root.childCount)

      fun splitIntoCategories(level: Int, rules: List<Rule>, parent: GrazieRulesTreeNode) {
        rules.groupBy { it.categories.getOrNull(level) }.entries
          .sortedWith(Comparator.comparing({ it.key }, nullsLast(Comparator.comparing { it.lowercase() })))
          .forEach { (category, catRules) ->
            if (category != null) {
              val categoryNode = GrazieRulesTreeNode(category)
              model.insertNodeInto(categoryNode, parent, parent.childCount)
              splitIntoCategories(level + 1, catRules, categoryNode)
            }
            else {
              catRules.sortedBy { it.presentableName.lowercase() }.forEach { rule ->
                model.insertNodeInto(GrazieRulesTreeNode(rule), parent, parent.childCount)
              }
            }
          }
      }
      splitIntoCategories(0, rules, langNode)
    }

    model.setRoot(root)
    root.resetMark(apply(GrazieConfig.get()))
    model.nodeChanged(root)
  }
}

internal fun allRules(state: GrazieConfig.State = GrazieConfig.get()): Map<Lang, List<Rule>> {
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