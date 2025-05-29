// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.webSymbols.PolySymbolTypeSupport

data class PolySymbolTypeSupportTypeReferenceData(
  override val module: String?,
  override val name: String,
): PolySymbolTypeSupport.TypeReference
