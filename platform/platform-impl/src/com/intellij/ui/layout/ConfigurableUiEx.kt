// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.options.ConfigurableUi

@Deprecated("Port to Kotlin UI DSL, this interface is not needed")
interface ConfigurableUiEx<S> : ConfigurableUi<S> {
  fun buildUi(builder: LayoutBuilder)
}
