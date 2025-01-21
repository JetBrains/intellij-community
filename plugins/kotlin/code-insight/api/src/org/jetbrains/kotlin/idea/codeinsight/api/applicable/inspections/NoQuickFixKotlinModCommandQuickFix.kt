// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtElement

class NoQuickFixKotlinModCommandQuickFix<ELEMENT : KtElement>(val familyNameProvider: () -> @IntentionFamilyName String): KotlinModCommandQuickFix<ELEMENT>() {
    @Suppress("HardCodedStringLiteral")
    override fun getFamilyName(): @IntentionFamilyName String {
        return familyNameProvider()
    }

    override fun applyFix(project: Project, element: ELEMENT, updater: ModPsiUpdater) {
        // intentionally nothing
    }
}