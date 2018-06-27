// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl

import com.intellij.structuralsearch.MatchVariableConstraint
import com.intellij.structuralsearch.plugin.ui.Configuration
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration

class ConfigurationBuilder(val configuration: Configuration) {
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

  val MatchVariableConstraint.target: Unit
    //todo definitely hack
    get() {
      isPartOfSearchResults = true
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
