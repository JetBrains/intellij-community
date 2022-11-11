// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

@ApiStatus.NonExtendable
interface CollapsibleRow : Row {

  var expanded: Boolean

  fun setTitle(@NlsContexts.BorderTitle title: String)

  fun setTitleFont(font: Font)

  fun addExpandedListener(action: (Boolean) -> Unit)

}