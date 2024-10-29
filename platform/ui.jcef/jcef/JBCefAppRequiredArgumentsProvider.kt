// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.extensions.ExtensionPointName

interface JBCefAppRequiredArgumentsProvider {
  companion object {
    var EP: ExtensionPointName<JBCefAppRequiredArgumentsProvider> = ExtensionPointName("com.intellij.jcef.appRequiredArgumentsProvider")

    @JvmStatic
    fun getProviders(): List<JBCefAppRequiredArgumentsProvider> = EP.extensionList
  }

  val options: List<String>
}