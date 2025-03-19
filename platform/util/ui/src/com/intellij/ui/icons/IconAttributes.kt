// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

@JvmInline
internal value class IconAttributes(@JvmField val flags: Int) {
  constructor(isDark: Boolean = false,
              isDarkSet: Boolean = false,
              useStroke: Boolean = false) : this(
    (if (isDark) 0b01 else 0) or
      (if (isDarkSet) 0b10 else 0) or
      (if (useStroke) 0b100 else 0)
  )

  val isDark: Boolean
    get() = flags and 0b01 != 0

  val isDarkSet: Boolean
    get() = flags and 0b10 != 0

  val useStroke: Boolean
    get() = flags and 0b100 != 0

  fun copy(isDark: Boolean = this.isDark,
           isDarkSet: Boolean = this.isDarkSet,
           useStroke: Boolean = this.useStroke): IconAttributes {
    return IconAttributes(isDark = isDark, isDarkSet = isDarkSet, useStroke = useStroke)
  }
}