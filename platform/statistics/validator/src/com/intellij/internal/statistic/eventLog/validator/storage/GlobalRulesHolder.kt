// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors
import java.util.*

class GlobalRulesHolder(private val myGlobalEnums: Map<String, Set<String>>?, private val myGlobalRegexps: Map<String, String>?) {
  constructor(globalRules: EventGroupRemoteDescriptors.GroupRemoteRule?) : this(globalRules?.enums, globalRules?.regexps)

  private val myGlobalRegexpsCache: MutableMap<String, RegexpValidationRule> = HashMap()
  private val myGlobalEnumsCache: MutableMap<String, EnumValidationRule> = HashMap()

  fun getEnumValidationRules(enumRef: String): EnumValidationRule? {
    val cachedValue = myGlobalEnumsCache[enumRef]
    if (cachedValue != null) return cachedValue
    val globalEnum = myGlobalEnums?.get(enumRef)
    if (globalEnum != null) {
      val rule = EnumValidationRule(globalEnum)
      myGlobalEnumsCache[enumRef] = rule
      return rule
    }
    return null
  }

  fun getRegexpValidationRules(regexpRef: String): RegexpValidationRule? {
    val cachedValue = myGlobalRegexpsCache[regexpRef]
    if (cachedValue != null) return cachedValue
    val globalRegexp = myGlobalRegexps?.get(regexpRef)
    if (globalRegexp != null) {
      val rule = RegexpValidationRule(globalRegexp)
      myGlobalRegexpsCache[regexpRef] = rule
      return rule
    }
    return null
  }
}