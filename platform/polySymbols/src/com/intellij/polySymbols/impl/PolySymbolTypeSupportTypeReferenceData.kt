// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.utils.PolySymbolTypeSupport

data class PolySymbolTypeSupportTypeReferenceData(
  override val module: String?,
  override val name: String,
) : PolySymbolTypeSupport.TypeReference
