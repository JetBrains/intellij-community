// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueTextModificationPreparator
import com.intellij.xdebugger.frame.XValueTextModificationPreparatorProvider

internal object XValueTextModificationPreparatorProviders {
  private val EP_NAME = ExtensionPointName<XValueTextModificationPreparatorProvider>("com.intellij.xdebugger.xTextValueModificationPreparatorProvider")

  fun getPreparator(value: XValue): XValueTextModificationPreparator? {
    return EP_NAME.extensionList.firstNotNullOfOrNull { it.getTextValuePreparator(value) }
  }
}
