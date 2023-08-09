// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder

internal class FirAssignmentPluginQuickFixRegistrar : KotlinQuickFixRegistrar() {

    private val fixes = KtQuickFixesListBuilder.registerPsiQuickFix {}

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(fixes)
}