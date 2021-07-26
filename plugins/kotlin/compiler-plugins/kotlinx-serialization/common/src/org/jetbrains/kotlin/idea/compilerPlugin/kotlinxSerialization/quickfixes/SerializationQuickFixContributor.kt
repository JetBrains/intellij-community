// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.quickfixes

import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlinx.serialization.compiler.diagnostic.SerializationErrors
import org.jetbrains.kotlinx.serialization.idea.quickfixes.JsonRedundantDefaultQuickFix
import org.jetbrains.kotlinx.serialization.idea.quickfixes.JsonRedundantQuickFix

class SerializationQuickFixContributor : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        quickFixes.register(SerializationErrors.INCORRECT_TRANSIENT, AddKotlinxSerializationTransientImportQuickFix.Factory)
        quickFixes.register(SerializationErrors.JSON_FORMAT_REDUNDANT_DEFAULT, JsonRedundantDefaultQuickFix.Factory)
        quickFixes.register(SerializationErrors.JSON_FORMAT_REDUNDANT, JsonRedundantQuickFix.Factory)
    }
}
