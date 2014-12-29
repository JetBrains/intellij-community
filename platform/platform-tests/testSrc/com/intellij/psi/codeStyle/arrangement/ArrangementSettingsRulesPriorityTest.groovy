/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement

import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.INTERFACE
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.GETTER
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.OVERRIDDEN
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PROTECTED
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.SETTER
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC

class ArrangementSettingsRulesPriorityTest extends AbstractRearrangerTest {

  def getRulesSortedByPriority(List<StdArrangementMatchRule> rules) {
    def sectionRules = getSectionRules(rules)
    List<ArrangementGroupingRule> groupingRules = Collections.emptyList();
    def settings = new StdArrangementSettings(groupingRules, sectionRules)
    return settings.getRulesSortedByPriority()
  }
    
  void "test getter and setter rules comes first"() {
    def getter = rule(GETTER)
    def setter = rule(SETTER)
    def publicMethod = rule(PUBLIC, METHOD)
    def method = rule(METHOD)

    def rules = getRulesSortedByPriority([rule(INTERFACE), publicMethod, method, getter, rule(FIELD), setter, rule(FIELD, PUBLIC)])
    assert rules.indexOf(getter) < rules.indexOf(publicMethod) && rules.indexOf(getter) < rules.indexOf(method)
    assert rules.indexOf(setter) < rules.indexOf(publicMethod) && rules.indexOf(setter) < rules.indexOf(method)
  }

  void "test overriden rule comes before"() {
    def publicMethod = rule(PUBLIC, METHOD)
    def protectedMethod = rule(PROTECTED, METHOD)
    def method = rule(METHOD)
    def getter = rule(GETTER)
    def setter = rule(SETTER)
    def overridenMethod = rule(OVERRIDDEN)

    def rules = [rule(INTERFACE), publicMethod, method, rule(FIELD), rule(FIELD, PUBLIC), overridenMethod, protectedMethod, getter, setter]
    def sortedRules = getRulesSortedByPriority(rules)
    def overridenRuleIndex = sortedRules.indexOf(overridenMethod)

    assert overridenRuleIndex < sortedRules.indexOf(publicMethod)
    assert overridenRuleIndex < sortedRules.indexOf(method)
    assert overridenRuleIndex < sortedRules.indexOf(protectedMethod)
    assert overridenRuleIndex < sortedRules.indexOf(getter)
    assert overridenRuleIndex < sortedRules.indexOf(setter)
  }
}
