// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.util

sealed class QuickFixCheck {
  data class ExpectFix(
    val after: String,
    val caretAnchor: String = "",
    val expectedFunctionName: String? = null,
  ) : QuickFixCheck()

  data class ExpectNoFix(
    val anchors: List<String>,
  ) : QuickFixCheck()
}

// -"anchor" -> ExpectNoFix
operator fun String.unaryMinus() = QuickFixCheck.ExpectNoFix(listOf(this))

// +"expected code" -> ExpectFix
operator fun String.unaryPlus() = QuickFixCheck.ExpectFix(after = this)

// ... at "anchor" -> ExpectFix
infix fun QuickFixCheck.ExpectFix.at(anchor: String) = copy(caretAnchor = anchor)

// ... with "functionName" -> ExpectFix
infix fun QuickFixCheck.ExpectFix.with(name: String) = copy(expectedFunctionName = name)