// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements

import com.intellij.webSymbols.WebSymbol

interface CustomElementsSymbol : WebSymbol {


  companion object {

    const val NAMESPACE_CUSTOM_ELEMENTS_MANIFEST: String = "custom-elements-manifest"

    const val KIND_CEM_PACKAGES: String = "packages"
    const val KIND_CEM_MODULES: String = "modules"
    const val KIND_CEM_DECLARATIONS: String = "declarations"

  }

}