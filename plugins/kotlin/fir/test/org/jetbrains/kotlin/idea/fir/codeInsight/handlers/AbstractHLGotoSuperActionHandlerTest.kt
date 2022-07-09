// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight.handlers

import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.fir.codeInsight.handlers.superDeclarations.KotlinSuperDeclarationsInfo
import org.jetbrains.kotlin.idea.fir.codeInsight.handlers.superDeclarations.KotlinSuperDeclarationsInfoService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths

abstract class AbstractHLGotoSuperActionHandlerTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun doTest(testFilePath: String) {
        val ktFile = myFixture.configureByFile(testFilePath) as KtFile
        val data = KotlinSuperDeclarationsInfoService.getForDeclarationAtCaret(ktFile, myFixture.editor)
            ?: error("Caret should point to a declaration which may have super declarations")
        val actual = render(data)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }

    private fun render(info: KotlinSuperDeclarationsInfo): String = buildString {
        for (superDeclaration in info.superDeclarations) {
            appendLine(superDeclaration::class.simpleName!! + ": " + superDeclaration.kotlinFqName)
        }
    }
}