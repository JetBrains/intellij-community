// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.nio.file.Paths

abstract class AbstractHLImplementationSearcherTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(testFilePath: String) {
        myFixture.configureByFile(testFilePath) as KtFile

        val declarationAtCaret = myFixture.elementAtCaret.parentOfType<KtDeclaration>(withSelf = true)
            ?: error("No declaration found at caret")

        val result = ActionUtil.underModalProgress(project, "") { DefinitionsScopedSearch.search(declarationAtCaret).toList() }
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }

    private fun render(declarations: List<PsiElement>): String = buildList {
        for (declaration in declarations) {
          val name = declaration.kotlinFqName ?: declaration.declarationName()
          add(declaration::class.simpleName!! + ": " + name)
        }
    }.sorted().joinToString(separator = "\n")

    private fun PsiElement.declarationName() = when (this) {
        is KtNamedDeclaration -> nameAsSafeName.asString()
        is PsiNameIdentifierOwner -> nameIdentifier?.text ?: "<no name>"
        else -> error("Unknown declaration ${this::class.simpleName}")
    }
}