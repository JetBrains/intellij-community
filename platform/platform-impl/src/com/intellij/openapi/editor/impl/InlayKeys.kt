// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.util.Key

object InlayKeys {
  @JvmField
  val ID_BEFORE_DISPOSAL = Key.create<Long>("inlay.id.before.disposal")
  @JvmField
  internal val OFFSET_BEFORE_DISPOSAL = Key.create<Int>("inlay.offset.before.disposal")
  @JvmField
  internal val ORDER_BEFORE_DISPOSAL = Key.create<Int>("inlay.order.before.disposal")
}
