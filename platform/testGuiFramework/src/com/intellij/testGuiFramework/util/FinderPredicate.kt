// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

typealias FinderPredicate = (String, String) -> Boolean

object Predicate{
  val equality: FinderPredicate = { left: String, right: String -> left == right }
  val withVersion: FinderPredicate = { left: String, right: String ->
    val pattern = Regex(",\\s+\\(.*\\)$")
    if (right.contains(pattern))
      left == right.dropLast(right.length - right.indexOfLast { it == ',' })
    else left == right
  }

}