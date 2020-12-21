// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

interface CollapsibleRow : Row {

  var expanded: Boolean

  fun setText(text: String)
  
  fun addExpandedListener(action: (Boolean) -> Unit)
}