// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.psi.util.descendants
import org.jetbrains.kotlin.idea.codeinsight.utils.renderTrimmed
import org.jetbrains.kotlin.idea.highlighter.kotlinUnresolvedReferenceKinds
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractK2UnresolvedReferenceKindHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    protected open fun doTest(path: String) {
        myFixture.configureByFile(mainFile())
        myFixture.doHighlighting()

        val unresolvedElements = myFixture.file.descendants().mapNotNull { psiElement ->
            if (psiElement !is KtElement) return@mapNotNull null
            val kinds = psiElement.kotlinUnresolvedReferenceKinds
            if (kinds.isEmpty()) return@mapNotNull null
            "${psiElement.renderTrimmed()}: $kinds"
        }

        val expectedFile = mainFile().resolveSibling(mainFile().name + ".unresolvedKinds")
        KotlinTestUtils.assertEqualsToFile(expectedFile, unresolvedElements.joinToString("\n")) { it }
    }
}