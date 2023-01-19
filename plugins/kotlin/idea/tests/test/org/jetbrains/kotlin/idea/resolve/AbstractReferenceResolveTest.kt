// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.completion.test.configureWithExtraFile
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.junit.Assert
import kotlin.test.assertTrue

abstract class AbstractReferenceResolveTest : KotlinLightCodeInsightFixtureTestCase() {
    class ExpectedResolveData(private val shouldBeUnresolved: Boolean?, val referenceString: String) {

        fun shouldBeUnresolved(): Boolean {
            return shouldBeUnresolved!!
        }
    }

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk8()

    protected open fun doTest(path: String) {
        assert(path.endsWith(".kt")) { path }
        myFixture.configureWithExtraFile(path, ".Data")
        val controlDirective = if (isFirPlugin()) {
            IgnoreTests.DIRECTIVES.IGNORE_FIR
        } else {
            IgnoreTests.DIRECTIVES.IGNORE_FE10
        }
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), controlDirective) {
            performChecks()
        }
    }

    protected open fun performAdditionalResolveChecks(results: List<PsiElement>) {}

    protected fun performChecks() {
        if (InTextDirectivesUtils.isDirectiveDefined(myFixture.file.text, MULTIRESOLVE)) {
            doMultiResolveTest()
        } else {
            doSingleResolveTest()
        }
    }

    private fun doSingleResolveTest() {
        forEachCaret { index, offset ->
            val expectedResolveData = readResolveData(myFixture.file.text, index, refMarkerText)
            val psiReference = wrapReference(myFixture.file.findReferenceAt(offset))
            checkReferenceResolve(expectedResolveData, offset, psiReference, render  = { this.render(it) }) { resolveTo ->
                checkResolvedTo(resolveTo)
                performAdditionalResolveChecks(listOf(resolveTo))
            }
        }
    }

    protected open fun render(element: PsiElement) : String {
        return element.renderAsGotoImplementation()
    }

    open fun checkResolvedTo(element: PsiElement) {
        // do nothing
    }

    open fun wrapReference(reference: PsiReference?): PsiReference? = reference
    open fun wrapReference(reference: PsiPolyVariantReference): PsiPolyVariantReference = reference

    private fun doMultiResolveTest() {
        forEachCaret { index, offset ->
            val expectedReferences = getExpectedReferences(myFixture.file.text, index, refMarkerText)

            val psiReference = myFixture.file.findReferenceAt(offset)
            assertTrue(psiReference is PsiPolyVariantReference)
            psiReference as PsiPolyVariantReference

            val results = executeOnPooledThreadInReadAction {
                wrapReference(psiReference).multiResolve(true).map { result ->
                    result.element
                        ?:  UsefulTestCase.fail("Reference ${psiReference} was resolved to ${result} without psi element") as Nothing
                }
            }

            performAdditionalResolveChecks(results)

            val actualResolvedTo = mutableListOf<String>()
            for (result in results) {
                actualResolvedTo.add(render(result))
            }

            UsefulTestCase.assertOrderedEquals("Not matching for reference #$index", actualResolvedTo.sorted(), expectedReferences.sorted())
        }
    }

    private fun forEachCaret(action: (index: Int, offset: Int) -> Unit) {
        val offsets = myFixture.editor.caretModel.allCarets.map { it.offset }
        val singleCaret = offsets.size == 1
        for ((index, offset) in offsets.withIndex()) {
            action(if (singleCaret) -1 else index + 1, offset)
        }
    }

    override fun getDefaultProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceNoSources()

    open val refMarkerText: String = "REF"

    companion object {
        const val MULTIRESOLVE: String = "MULTIRESOLVE"
        const val REF_EMPTY: String = "REF_EMPTY"

        fun readResolveData(fileText: String, index: Int, refMarkerText: String = "REF"): ExpectedResolveData {
            val shouldBeUnresolved = InTextDirectivesUtils.isDirectiveDefined(fileText, REF_EMPTY)
            val refs = getExpectedReferences(fileText, index, refMarkerText)

            val referenceToString: String
            if (shouldBeUnresolved) {
                Assert.assertTrue("REF: directives will be ignored for $REF_EMPTY test: $refs", refs.isEmpty())
                referenceToString = "<empty>"
            } else {
                assertTrue(
                    refs.size == 1,
                    "Must be a single ref: $refs.\nUse $MULTIRESOLVE if you need multiple refs\nUse $REF_EMPTY for an unresolved reference"
                )
                referenceToString = refs[0]
                Assert.assertNotNull("Test data wasn't found, use \"// REF: \" directive", referenceToString)
            }

            return ExpectedResolveData(shouldBeUnresolved, referenceToString)
        }

        // purpose of this helper is to deal with the case when navigation element is a file
        // see ReferenceResolveInJavaTestGenerated.testPackageFacade()
        private fun getExpectedReferences(text: String, index: Int, refMarkerText: String): List<String> {
            val prefix = if (index > 0) "// $refMarkerText$index:" else "// $refMarkerText:"
            return InTextDirectivesUtils.findLinesWithPrefixesRemoved(text, prefix)
        }

        fun checkReferenceResolve(
            expectedResolveData: ExpectedResolveData,
            offset: Int,
            psiReference: PsiReference?,
            render: (PsiElement) -> String = { it.renderAsGotoImplementation() },
            checkResolvedTo: (PsiElement) -> Unit = {}
        ) {
            val expectedString = expectedResolveData.referenceString
            if (psiReference != null) {
                val resolvedTo = executeOnPooledThreadInReadAction { psiReference.resolve() }
                if (resolvedTo != null) {
                    checkResolvedTo(resolvedTo)
                    val resolvedToElementStr = replacePlaceholders(render(resolvedTo))
                    assertEquals(
                        "Found reference to '$resolvedToElementStr', but '$expectedString' was expected",
                        expectedString,
                        resolvedToElementStr
                    )
                } else {
                    if (!expectedResolveData.shouldBeUnresolved()) {
                        assertNull(
                            "Element $psiReference (${psiReference.element
                                .text}) wasn't resolved to anything, but $expectedString was expected", expectedString
                        )
                    }
                }
            } else {
                assertNull("No reference found at offset: $offset, but one resolved to $expectedString was expected", expectedString)
            }
        }

        private fun replacePlaceholders(actualString: String): String {
            val replaced = PathUtil.toSystemIndependentName(actualString)
                .replace(IDEA_TEST_DATA_DIR.path, "/<test dir>")
                .replace("//", "/") // additional slashes to fix discrepancy between windows and unix
            if ("!/" in replaced) {
                return replaced.replace(replaced.substringBefore("!/"), "<jar>")
            }
            return replaced
        }
    }
}

private fun <R> executeOnPooledThreadInReadAction(action: () -> R): R =
    ApplicationManager.getApplication().executeOnPooledThread<R> { runReadAction(action) }.get()
