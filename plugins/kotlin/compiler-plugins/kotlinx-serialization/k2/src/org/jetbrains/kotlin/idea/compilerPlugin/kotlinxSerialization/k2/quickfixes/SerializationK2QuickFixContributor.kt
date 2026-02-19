// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.k2.quickfixes

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder

internal class SerializationK2QuickFixContributor : KotlinQuickFixRegistrar() {
    override val list: KotlinQuickFixesList
        get() = KtQuickFixesListBuilder.registerPsiQuickFix {
            registerFactory(JsonFormatRedundantDefaultFixFactory.replaceWithInstanceFactory)
            registerFactory(IncorrectTransientFixFactory.useKotlinxSerializationTransientFactory)
            registerFactory(JsonFormatRedundantFixFactory.extractToProperty)
        }
}
