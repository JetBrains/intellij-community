// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.webSymbols.WebSymbolTypeSupport

data class WebSymbolTypeSupportTypeReferenceData(
  override val module: String?,
  override val name: String,
): WebSymbolTypeSupport.TypeReference
