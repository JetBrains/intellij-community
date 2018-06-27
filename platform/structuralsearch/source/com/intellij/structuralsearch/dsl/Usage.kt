// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl

import com.intellij.structuralsearch.plugin.ui.Configuration

fun main(args: Array<String>) {
  val pattern: Configuration = pattern {
    name = "SSR"
    pattern = "class \$className\$ extends \$anotherClassName\$ {}"
    "className" {
      regExp = "MyClass"
      target
    }
    "anotherClassName" {
      regExp = "MatchOptions"
      isInvertRegExp = true
    }
    context = "Java"
  }
}