// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.psi.callableIdIfNotLocal
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinGoToSuperDeclarationsHandlerTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    protected fun performTest() {
        val file = myFixture.configureByMainPath() as KtFile
        val superDeclarations = KotlinGoToSuperDeclarationsHandler.findSuperDeclarations(file, editor.caretModel.offset)
        val actualText = render(superDeclarations?.items ?: emptyList())
        checkTextByExpectedPath(".expected", actualText)
    }

    private fun render(superDeclarations: List<SuperDeclaration>): String {
        return buildString {
            for (superDeclaration in superDeclarations) {
                val description = when (val declaration = superDeclaration.declaration.element) {
                    is KtClassOrObject -> declaration.classIdIfNonLocal?.toString()
                    is KtCallableDeclaration -> declaration.callableIdIfNotLocal?.toString()
                    else -> declaration?.javaClass?.simpleName
                }
                appendLine(superDeclaration::class.simpleName + ": " + (description ?: "null"))
            }
        }
    }
}