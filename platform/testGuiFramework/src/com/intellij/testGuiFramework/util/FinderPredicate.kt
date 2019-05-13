// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

typealias FinderPredicate = (String, String) -> Boolean

object Predicate{
  val equality: FinderPredicate = { found: String, wanted: String -> found == wanted }
  val notEquality: FinderPredicate = { found: String, wanted: String -> found != wanted }
  val withVersion: FinderPredicate = { found: String, wanted: String ->
    val pattern = Regex("\\s+\\(.*\\)$")
    if (found.contains(pattern)) {
      pattern.split(found).first().trim() == wanted
    }
    else found == wanted
  }
  val startWith: FinderPredicate = {found: String, wanted: String -> found.startsWith(wanted)}

}