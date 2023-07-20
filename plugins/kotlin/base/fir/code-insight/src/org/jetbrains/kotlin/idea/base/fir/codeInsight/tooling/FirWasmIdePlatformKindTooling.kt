// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractWasmIdePlatformKindTooling

class FirWasmIdePlatformKindTooling : AbstractWasmIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = SymbolBasedGenericTestIconProvider
}