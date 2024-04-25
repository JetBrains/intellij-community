// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractWasmJsIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractWasmWasiIdePlatformKindTooling

class Fe10WasmJsIdePlatformKindTooling : AbstractWasmJsIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = Fe10GenericTestIconProvider
}

class Fe10WasmWasiIdePlatformKindTooling : AbstractWasmWasiIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = Fe10GenericTestIconProvider
}