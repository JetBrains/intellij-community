// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

class SymbolBasedKotlinAnyVariableMacro : SymbolBasedAbstractKotlinVariableMacro() {
    override fun getName() = "kotlinAnyVariable"
    override fun getPresentableName() = "kotlinAnyVariable()"

    override val filterByExpectedType: Boolean
        get() = false
}