// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement

import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*
import java.util.*

class ArrangementSettingsRulesPriorityTest : AbstractRearrangerTest() {

  private fun getRulesSortedByPriority(rules: List<StdArrangementMatchRule>): List<StdArrangementMatchRule> {
    val sectionRules = getSectionRules(rules)
    val groupingRules: List<ArrangementGroupingRule> = Collections.emptyList()
    val settings = StdArrangementSettings(groupingRules, sectionRules)
    return settings.getRulesSortedByPriority() as List<StdArrangementMatchRule>
  }

  fun testGetterAndSetterRulesComesFirst() {
    val getter = rule(GETTER)
    val setter = rule(SETTER)
    val publicMethod = rule(PUBLIC, METHOD)
    val method = rule(METHOD)

    val rules = getRulesSortedByPriority(listOf(rule(INTERFACE), publicMethod, method, getter, rule(FIELD), setter, rule(FIELD, PUBLIC)))

    assertTrue(rules.indexOf(getter) < rules.indexOf(publicMethod) && rules.indexOf(getter) < rules.indexOf(method))
    assertTrue(rules.indexOf(setter) < rules.indexOf(publicMethod) && rules.indexOf(setter) < rules.indexOf(method))
  }

  fun testOverridenRuleComesBefore() {
    val publicMethod = rule(PUBLIC, METHOD)
    val protectedMethod = rule(PROTECTED, METHOD)
    val method = rule(METHOD)
    val getter = rule(GETTER)
    val setter = rule(SETTER)
    val overridenMethod = rule(OVERRIDDEN)

    val rules = listOf(rule(INTERFACE), publicMethod, method, rule(FIELD), rule(FIELD, PUBLIC), overridenMethod, protectedMethod, getter,
                       setter)
    val sortedRules = getRulesSortedByPriority(rules)
    val overridenRuleIndex = sortedRules.indexOf(overridenMethod)

    assertTrue(overridenRuleIndex < sortedRules.indexOf(publicMethod))
    assertTrue(overridenRuleIndex < sortedRules.indexOf(method))
    assertTrue(overridenRuleIndex < sortedRules.indexOf(protectedMethod))
    assertTrue(overridenRuleIndex < sortedRules.indexOf(getter))
    assertTrue(overridenRuleIndex < sortedRules.indexOf(setter))
  }
}