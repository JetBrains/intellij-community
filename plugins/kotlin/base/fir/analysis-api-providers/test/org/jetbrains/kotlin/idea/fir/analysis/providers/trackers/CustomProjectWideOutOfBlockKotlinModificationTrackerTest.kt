// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.trackers

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions.assertNotEquals
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class CustomProjectWideOutOfBlockKotlinModificationTrackerTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun `test remove block from getter`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int get(){}") as KtFile
        val getter = (file.declarations.first() as KtProperty).getter!!
        doBodyRemoveTest(getter)
    }

    fun `test remove expression body from getter`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int get()=4") as KtFile
        val getter = (file.declarations.first() as KtProperty).getter!!
        doBodyRemoveTest(getter)
    }

    fun `test remove block from setter`() {
        val file = myFixture.configureByText("usage.kt", "var i: Int set(value){}") as KtFile
        val setter = (file.declarations.first() as KtProperty).setter!!
        doBodyRemoveTest(setter)
    }

    fun `test remove expression body from setter`() {
        val file = myFixture.configureByText("usage.kt", "var i: Int set(value)=Unit") as KtFile
        val setter = (file.declarations.first() as KtProperty).setter!!
        doBodyRemoveTest(setter)
    }

    fun `test remove block from function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo(): Int {}") as KtFile
        val function = file.declarations.first() as KtNamedFunction
        doBodyRemoveTest(function)
    }

    fun `test remove expression body from function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo():Int =4") as KtFile
        val function = file.declarations.first() as KtNamedFunction
        doBodyRemoveTest(function)
    }

    fun `test remove block from local function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo() { fun ba<caret>r(){} }") as KtFile
        val function = (file.declarations.first() as KtNamedFunction).bodyBlockExpression!!.statements.first() as KtNamedFunction
        assertEquals("bar", function.name)

        doBodyRemoveTest(
            declaration = function,
            assertionAfterTransformation = { before, after ->
                assertEquals(before, after)
            }
        )
    }

    fun `test add block to function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo():Int") as KtFile
        val function = file.declarations.first() as KtNamedFunction
        doBlockAddTest(function)
    }

    fun `test expression body to function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo():Int=") as KtFile
        val function = file.declarations.first() as KtNamedFunction
        doBodyExpressionAddTest(function)
    }

    fun `test add block to getter`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int get()") as KtFile
        val getter = (file.declarations.first() as KtProperty).getter!!
        doBlockAddTest(getter)
    }

    fun `test expression body to getter`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int get()=") as KtFile
        val getter = (file.declarations.first() as KtProperty).getter!!
        doBodyExpressionAddTest(getter)
    }

    fun `test add block to setter`() {
        val file = myFixture.configureByText("usage.kt", "var i: Int set(value)") as KtFile
        val setter = (file.declarations.first() as KtProperty).setter!!
        doBlockAddTest(setter)
    }

    fun `test expression body to setter`() {
        val file = myFixture.configureByText("usage.kt", "var i: Int set(value)=") as KtFile
        val setter = (file.declarations.first() as KtProperty).setter!!
        doBodyExpressionAddTest(setter)
    }

    fun `test add block to local function`() {
        val file = myFixture.configureByText("usage.kt", "fun foo():Int { fun ba<caret>r() }") as KtFile
        val function = (file.declarations.first() as KtNamedFunction).bodyBlockExpression!!.statements.first() as KtNamedFunction
        assertEquals("bar", function.name)

        doBlockAddTest(
            declaration = function,
            assertionAfterTransformation = { before, after ->
                assertEquals(before, after)
            }
        )
    }

    fun `test add initializer to property`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int =") as KtFile
        val property = file.declarations.first() as KtProperty
        doTest(
            actionUnderWriteAction = { property.add(buildPsi { createExpression("4") }) },
            assertion = { before, after ->
                assertNotNull(property.initializer)
                assertNotEquals(before, after)
            }
        )
    }

    fun `test remove initializer from property`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int = 4") as KtFile
        val property = file.declarations.first() as KtProperty
        doTest(
            actionUnderWriteAction = { property.initializer!!.delete() },
            assertion = { before, after ->
                assertNull(property.initializer)
                assertNotEquals(before, after)
            }
        )
    }

    fun `test add delegate to property`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int ") as KtFile
        val property = file.declarations.first() as KtProperty
        doTest(
            actionUnderWriteAction = { property.add(buildPsi { createPropertyDelegate(buildPsi { createExpression("l") }) }) },
            assertion = { before, after ->
                assertNotNull(property.delegate)
                assertNotEquals(before, after)
            }
        )
    }

    fun `test remove delegate from property`() {
        val file = myFixture.configureByText("usage.kt", "val i: Int by l") as KtFile
        val property = file.declarations.first() as KtProperty
        doTest(
            actionUnderWriteAction = { property.delegate!!.delete() },
            assertion = { before, after ->
                assertNull(property.delegate)
                assertNotEquals(before, after)
            }
        )
    }

    //TODO
    fun `_test reorder accessors`() {
        val file = myFixture.configureByText("usage.kt", """var x: String
    get() {
        return ""
    }
    set(v: String) {
        // test
    }
""") as KtFile

        val property = file.declarations.first() as KtProperty
        val getter = property.getter!!
        val docManager = PsiDocumentManager.getInstance(project)
        val document = docManager.getDocument(file)!!
        val text = getter.text

        doTest(
            actionUnderWriteAction = {
                document.deleteString(getter.textRange.startOffset, getter.textRange.endOffset)
                document.insertString(document.textLength - 1, text)
                docManager.commitDocument(document)
            },
            assertion = { before, after ->
                assertNotEquals(before, after)
            }
        )
    }

    private inline fun <T> buildPsi(action: KtPsiFactory.() -> T): T = KtPsiFactory(project).action()

    private fun doBodyRemoveTest(
        declaration: KtDeclarationWithBody,
        assertionAfterTransformation: (before: Long, after: Long) -> Unit = { before, after -> assertNotEquals(before, after) },
    ) {
        doTest(
            actionUnderWriteAction = { declaration.bodyExpression!!.delete() },
            assertion = assertionAfterTransformation,
        )
    }

    private fun doBlockAddTest(
        declaration: KtDeclarationWithBody,
        assertionAfterTransformation: (before: Long, after: Long) -> Unit = { before, after -> assertNotEquals(before, after) },
    ) {
        doTest(
            actionUnderWriteAction = { declaration.add(buildPsi { createBlock("") }) },
            assertion = { before, after ->
                assertNotNull(declaration.bodyBlockExpression)
                assertionAfterTransformation(before, after)
            }
        )
    }

    private fun doBodyExpressionAddTest(
        declaration: KtDeclarationWithBody,
        assertionAfterTransformation: (before: Long, after: Long) -> Unit = { before, after -> assertNotEquals(before, after) },
    ) {
        doTest(
            actionUnderWriteAction = { declaration.add(buildPsi { createExpression("4") }) },
            assertion = { before, after ->
                assertNotNull(declaration.bodyExpression)
                assertionAfterTransformation(before, after)
            }
        )
    }

    private fun doTest(actionUnderWriteAction: () -> Unit, assertion: (before: Long, after: Long) -> Unit) {
        val tracker = project.createProjectWideOutOfBlockModificationTracker()
        val before = tracker.modificationCount
        runUndoTransparentWriteAction {
            actionUnderWriteAction()
        }

        val after = tracker.modificationCount
        assertion(before, after)
    }
}