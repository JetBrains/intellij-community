// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.rules

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import java.util.*
import javax.swing.tree.DefaultTreeModel

class GrazieRulesTree(renderer: CheckboxTreeCellRenderer) : CheckboxTree(renderer, GrazieTreeNode()) {
  data class TreeState(val enabled: Set<String>, val disabled: Set<String>)

  init {
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

  private val langs = HashSet(GrazieConfig.get().enabledLanguages)
  private val state = HashMap<String, RuleWithLang>()

  val isModified: Boolean
    get() = state.values.any { it.lang in langs }

  fun addLang(lang: Lang) = langs.add(lang)
  fun removeLang(lang: Lang) = langs.remove(lang)

  fun state(): TreeState {
    val (enabled, disabled) = state.values.filter { it.lang in langs }.partition { it.enabledInTree }
    return TreeState(enabled.map { it.rule.id }.toSet(), disabled.map { it.rule.id }.toSet())
  }

  fun clearState() = state.clear()

  /** Will filter tree representation in UI */
  fun filter(filterString: String?) {
    if (!filterString.isNullOrBlank()) {
      reset(LangTool.allRulesWithLangs(langs).asSequence().map { (lang, categories) ->
        if (lang.displayName.contains(filterString, true)) lang to categories
        else lang to categories.map { (category, rules) ->
          if (category.category.getName(lang.jLanguage).contains(filterString, true)) category to rules
          else category to TreeSet(rules.filter { it.rule.description.contains(filterString, true) })
        }.toMap().filterValues { it.isNotEmpty() }
      }.toMap().filterValues { it.isNotEmpty() })
    }
    else {
      reset(LangTool.allRulesWithLangs(langs))
    }
  }

  private fun reset(rules: RulesMap) {
    val root = GrazieTreeNode()
    val model = model as DefaultTreeModel

    rules.forEach { (lang, categories) ->
      val langNode = GrazieTreeNode(lang)
      model.insertNodeInto(langNode, root, root.childCount)
      categories.forEach { (category, rules) ->
        val categoryNode = GrazieTreeNode(category)
        model.insertNodeInto(categoryNode, langNode, langNode.childCount)
        rules.forEach { rule ->
          model.insertNodeInto(GrazieTreeNode(rule), categoryNode, categoryNode.childCount)
        }
      }
    }

    with(root) {
      model.setRoot(this)
      resetMark(state)
      model.nodeChanged(this)
    }
  }

  override fun installSpeedSearch() {}
}
