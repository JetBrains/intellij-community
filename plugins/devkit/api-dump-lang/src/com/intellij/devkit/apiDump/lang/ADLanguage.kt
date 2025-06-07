// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.lang.Language

internal object ADLanguage : Language("ADLanguage") {
  override fun isCaseSensitive(): Boolean = true
}