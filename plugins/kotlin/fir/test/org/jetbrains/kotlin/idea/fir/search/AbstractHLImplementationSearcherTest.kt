package org.jetbrains.kotlin.idea.fir.search

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.nio.file.Paths

abstract class AbstractHLImplementationSearcherTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(testFilePath: String) {
        myFixture.configureByFile(testFilePath) as KtFile

        val declarationAtCaret = myFixture.elementAtCaret.parentOfType<KtDeclaration>(withSelf = true)
            ?: error("No declaration found at caret")

        val result = DefinitionsScopedSearch.search(declarationAtCaret).toList()
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun render(declarations: List<PsiElement>): String = buildList {
        for (declaration in declarations) {
            val name = declaration.getKotlinFqName() ?: declaration.declarationName()
            add(declaration::class.simpleName!! + ": " + name)
        }
    }.sorted().joinToString(separator = "\n")

    private fun PsiElement.declarationName() = when (this) {
        is KtNamedDeclaration -> nameAsSafeName.asString()
        is PsiNameIdentifierOwner -> nameIdentifier?.text ?: "<no name>"
        else -> error("Unknown declaration ${this::class.simpleName}")
    }
}