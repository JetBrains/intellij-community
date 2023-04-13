// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.json

import com.intellij.webSymbols.customElements.json.ClassField.Privacy

interface CustomElementsMember : CustomElementsContributionWithSource {

  val inheritedFrom: Reference?

  val privacy: Privacy?

  val static: Boolean?
}