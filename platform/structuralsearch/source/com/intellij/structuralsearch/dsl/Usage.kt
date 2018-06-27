// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl

import com.intellij.structuralsearch.plugin.ui.Configuration

fun main(args: Array<String>) {
  val pattern: Configuration = pattern {
    name = "SSR"
    pattern = "class \$className\$ extends \$anotherClassName\$ {}"
    "className" {
      regexp = "MyClass"
      regexp = !"MyClass"
      target
    }
    "anotherClassName" {
      text = "MatchOptions"
      text = !"MatchOptions"
      isInvertRegExp = true
    }
    "count" {
      count = 0
      count = atLeast(1)
      count = any
      count = 1..10
    }
    fileType = "Java"
    context = "File"
  }
}