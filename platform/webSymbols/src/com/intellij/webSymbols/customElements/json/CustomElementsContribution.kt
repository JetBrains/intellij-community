// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.json

interface CustomElementsContribution {

  val deprecated: Deprecated? get() = null

  val description: String? get() = null

  val name: String? get() = null

  val summary: String? get() = null

  val type: Type? get() = null

}