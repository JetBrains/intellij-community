// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.structuralsearch.MatchVariableConstraint
import com.intellij.structuralsearch.plugin.ui.Configuration
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration

class ConfigurationBuilder(val configuration: Configuration) {
  companion object {
    val anyRange = IntRange(0, Int.MAX_VALUE)
  }
  var name: String
    get() = configuration.name
    set(value) {
      configuration.name = value
    }
  var pattern: String
    get() = configuration.matchOptions.searchPattern
    set(value) {
      configuration.matchOptions.searchPattern = value
    }

  operator fun String.invoke(builder: MatchVariableConstraint.() -> Unit) {
    val constraint = MatchVariableConstraint()
    constraint.name = this
    constraint.apply(builder)
    configuration.matchOptions.addVariableConstraint(constraint)
  }

  val any: IntRange
    get() {
      return anyRange
    }
  val MatchVariableConstraint.target: Unit
  //todo definitely hack
    get() {
      isPartOfSearchResults = true
    }
  var MatchVariableConstraint.count: Any
    get() {
      return minCount..maxCount
    }
    set(value) {
      when (value) {
        is IntRange -> {
          minCount = value.start
          maxCount = value.endInclusive
        }
        is Int -> {
          minCount = value
          maxCount = value
        }
      }
    }
  fun MatchVariableConstraint.atLeast(from: Int): IntRange {
    return IntRange(from, Int.MAX_VALUE)
  }

  var ConfigurationBuilder.fileType: String
    get() {
      return configuration.matchOptions.fileType.name
    }
    set(value) {
      configuration.matchOptions.fileType = FileTypeManager.getInstance().findFileTypeByName(value.toUpperCase())
    }
  var ConfigurationBuilder.context: String
    get() {
      return configuration.matchOptions.patternContext
    }
    set(value) {
      configuration.matchOptions.patternContext = value
    }
}

fun pattern(builder: ConfigurationBuilder.() -> Unit): Configuration {
  val configuration = SearchConfiguration()
  val configurationBuilder = ConfigurationBuilder(configuration)
  configurationBuilder.apply(builder)
  return configuration;
}
