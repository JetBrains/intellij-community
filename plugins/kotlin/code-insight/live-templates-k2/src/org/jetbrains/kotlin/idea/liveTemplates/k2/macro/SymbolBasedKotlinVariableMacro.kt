// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

class SymbolBasedKotlinVariableMacro : SymbolBasedAbstractKotlinVariableMacro() {
    override fun getName() = "kotlinVariable"
    override fun getPresentableName() = "kotlinVariable()"

    override val filterByExpectedType: Boolean
        get() = true
}