// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.Rule
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes

@Suppress("EqualsOrHashCode")
internal class GrazieRulesTreeNode(userObject: Any? = null) : CheckedTreeNode(userObject) {
  val nodeText: String
    get() = when (val meta = userObject) {
      is Rule -> meta.presentableName
      is Lang -> meta.nativeName
      else -> (meta ?: "") as String // category or root
    }

  val attrs: SimpleTextAttributes
    get() {
      val meta = userObject
      val attributes = if (meta is Rule) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
      return if (differsFromDefault()) attributes.derive(-1, JBColor.BLUE, null, null) else attributes
    }

  private fun differsFromDefault(): Boolean {
    val meta = userObject
    if (meta is Rule) return meta.isEnabledByDefault != isChecked
    return children.orEmpty().any { (it as GrazieRulesTreeNode).differsFromDefault() }
  }

  internal fun findRuleNode(globalId: String): GrazieRulesTreeNode? {
    if ((userObject as? Rule)?.globalId == globalId) return this
    return children.orEmpty().asSequence().mapNotNull { (it as GrazieRulesTreeNode).findRuleNode(globalId) }.firstOrNull()
  }

  fun resetMark(state: GrazieConfig.State): Boolean {
    val meta = userObject
    if (meta is Rule) {
      isChecked = meta.isEnabledInState(state)
    }
    else {
      isChecked = false
      for (child in children()) {
        if (child is GrazieRulesTreeNode && child.resetMark(state)) {
          isChecked = true
        }
      }
    }

    return isChecked
  }

  override fun equals(other: Any?): Boolean {
    if (other is GrazieRulesTreeNode) {
      return userObject == other.userObject
    }

    return super.equals(other)
  }
}
