// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.modifiers

import com.intellij.structuralsearch.plugin.ui.modifier.ModifierAction
import com.intellij.structuralsearch.plugin.ui.modifier.ModifierProvider
import org.jetbrains.kotlin.idea.KotlinBundle

class KotlinModifierProvider : ModifierProvider {
    override fun getModifiers(): List<ModifierAction> = listOf(VarOnlyModifier(), ValOnlyModifier())
}

class VarOnlyModifier : OneStateModifier(
    KotlinBundle.lazyMessage("modifier.match.only.vars"),
    KotlinBundle.message("label.match.only.vars"),
    CONSTRAINT_NAME
) {

    companion object {
        const val CONSTRAINT_NAME: String = "kotlinVarOnly"
    }

}

class ValOnlyModifier : OneStateModifier(
    KotlinBundle.lazyMessage("modifier.match.only.vals"),
    KotlinBundle.message("label.match.only.vals"),
    CONSTRAINT_NAME
) {

    companion object {
        const val CONSTRAINT_NAME: String = "kotlinValOnly"
    }

}