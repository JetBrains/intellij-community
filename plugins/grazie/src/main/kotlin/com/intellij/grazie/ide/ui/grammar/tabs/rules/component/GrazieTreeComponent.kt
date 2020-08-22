// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules.*
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.ui.*
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class GrazieTreeComponent(onSelectionChanged: (meta: Any) -> Unit) : CheckboxTree(GrazieRulesTreeCellRenderer(), GrazieRulesTreeNode()),
                                                                     GrazieStateLifecycle, Disposable, GrazieUIComponent {
  private val state = CollectionFactory.createSmallMemoryFootprintMap<String, RuleWithLang>()
  private val filterComponent: GrazieRulesTreeFilter = GrazieRulesTreeFilter(this)

  private lateinit var myConnection: MessageBusConnection

  init {
    selectionModel.addTreeSelectionListener { event ->
      val meta = (event?.path?.lastPathComponent as DefaultMutableTreeNode).userObject
      if (meta != null) onSelectionChanged(meta)
    }

    addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val meta = node.userObject
        if (meta is RuleWithLang) {
          meta.enabledInTree = node.isChecked
          if (meta.enabled == meta.enabledInTree) {
            state.remove(meta.rule.id)
          }
          else {
            state[meta.rule.id] = meta
          }
        }
      }
    })
  }

  override fun installSpeedSearch() {
    TreeSpeedSearch(this) {
      when (val node = TreeUtil.getLastUserObject(it)) {
        is RuleWithLang -> node.rule.description
        is ComparableCategory -> node.category.getName(node.lang.jLanguage)
        is Lang -> node.nativeName
        else -> ""
      }
    }
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (prevState.enabledLanguages != newState.enabledLanguages) {
      resetTreeModel(LangTool.allRulesWithLangs(newState))
    }
  }

  override val component by lazy {
    panel {
      // register tree on languages list update from proofreading tab
      myConnection = service<GrazieInitializerManager>().register(this@GrazieTreeComponent)
      panel(constraint = BorderLayout.NORTH) {
        border = JBUI.Borders.emptyBottom(2)

        DefaultActionGroup().apply {
          val actionManager = CommonActionsManager.getInstance()
          val treeExpander = DefaultTreeExpander(this@GrazieTreeComponent)
          add(actionManager.createExpandAllAction(treeExpander, this@GrazieTreeComponent))
          add(actionManager.createCollapseAllAction(treeExpander, this@GrazieTreeComponent))

          add(ActionManager.getInstance().createActionToolbar("GrazieRulesPanel", this, true).component, BorderLayout.WEST)
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

  override fun isModified(state: GrazieConfig.State): Boolean = this.state.isNotEmpty()

  override fun reset(state: GrazieConfig.State) {
    this.state.clear()
    filterComponent.filter()
    if (isSelectionEmpty) setSelectionRow(0)
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State {
    val userDisabledRules = state.userDisabledRules.toMutableSet()
    val userEnabledRules = state.userEnabledRules.toMutableSet()

    val (enabled, disabled) = this.state.values.partition { it.enabledInTree }

    enabled.map { it.rule.id }.toSet().forEach { id ->
      userDisabledRules.remove(id)
      userEnabledRules.add(id)
    }

    disabled.map { it.rule.id }.toSet().forEach { id ->
      userDisabledRules.add(id)
      userEnabledRules.remove(id)
    }

    return state.copy(
      userEnabledRules = userEnabledRules,
      userDisabledRules = userDisabledRules
    )
  }

  override fun dispose() {
    filterComponent.dispose()
    myConnection.disconnect()
  }

  fun filter(str: String) {
    filterComponent.filter = str
    filterComponent.filter()
  }

  fun getCurrentFilterString(): String? = filterComponent.filter

  fun resetTreeModel(rules: RulesMap) {
    val root = GrazieRulesTreeNode()
    val model = model as DefaultTreeModel

    rules.forEach { (lang, categories) ->
      val langNode = GrazieRulesTreeNode(lang)
      model.insertNodeInto(langNode, root, root.childCount)

      categories.forEach { (category, rules) ->
        val categoryNode = GrazieRulesTreeNode(category)
        model.insertNodeInto(categoryNode, langNode, langNode.childCount)

        rules.forEach { rule ->
          model.insertNodeInto(GrazieRulesTreeNode(rule), categoryNode, categoryNode.childCount)
        }
      }
    }

    with(root) {
      model.setRoot(this)
      resetMark(state)
      model.nodeChanged(this)
    }
  }
}
