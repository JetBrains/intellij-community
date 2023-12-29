// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.test.ensureFilesResolved
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.addTypeArgument
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.io.File

class DanglingFileModuleInvalidationTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override fun isFirPlugin(): Boolean = true

    fun testNoChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", barCall)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            // Nothing to do
        }

        assertEquals(sessionBefore, sessionAfter)
        assert(sessionAfter.isValid)
    }

    fun testUnrelatedCodeFragmentChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment1 = KtBlockCodeFragment(project, "fragment1.kt", "bar<Int>()", "", barCall)
        val codeFragment2 = KtBlockCodeFragment(project, "fragment2.kt", "bar<String>()", "", barCall)
        ensureFilesResolved(codeFragment1, codeFragment2, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment2) {
            val codeFragment1BarCall = codeFragment1.findDescendantOfType<KtCallExpression> { it.text == "bar<Int>()" }!!
            codeFragment1BarCall.typeArguments[0].delete()
        }

        assertEquals(sessionBefore, sessionAfter)
        assert(sessionAfter.isValid)
    }

    fun testNestedCodeFragmentChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment1 = KtBlockCodeFragment(project, "fragment1.kt", "bar<Int>()", "", barCall)
        val codeFragment1BarCall = codeFragment1.findDescendantOfType<KtCallExpression> { it.text == "bar<Int>()" }!!

        val codeFragment2 = KtBlockCodeFragment(project, "fragment2.kt", "bar<String>()", "", codeFragment1BarCall)
        ensureFilesResolved(codeFragment1, codeFragment2, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment2) {
            codeFragment1BarCall.typeArguments[0].delete()
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testCodeFragmentChangeInsideFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = true)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val fakeBarCall = fakeFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", fakeBarCall)
        ensureFilesResolved(codeFragment, fakeFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            fakeBarCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testCodeFragmentChangeInsideUnrelatedFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = true)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", barCall)
        ensureFilesResolved(codeFragment, fakeFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val fakeBarCall = fakeFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!
            fakeBarCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
        }

        assertEquals(sessionBefore, sessionAfter)
        assert(sessionAfter.isValid)
    }

    fun testNonPhysicalFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = false)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val (sessionBefore, sessionAfter) = performModification(fakeFile) {
            // Do nothing
        }

        assert(sessionBefore.isValid)
        assertTrue(sessionBefore === sessionAfter)

        val (sessionBeforeWithPsiModification, sessionAfterWithPsiModification) = performModification(fakeFile) {
            PsiManager.getInstance(project).dropPsiCaches()
        }

        assert(!sessionBeforeWithPsiModification.isValid)
        assert(sessionAfterWithPsiModification.isValid)
    }

    fun testContextInBodyChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", barCall)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val ktPsiFactory = KtPsiFactory(project, markGenerated = false)
            barCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testContextDependencyChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", barCall)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            srcModule.addKotlinStdlib()
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testFragmentInBodyChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar<Any>()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar<Any>()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar()", "", barCall)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val codeFragmentBarCall = codeFragment.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!
            val ktPsiFactory = KtPsiFactory(project, markGenerated = false)
            codeFragmentBarCall.addTypeArgument(ktPsiFactory.createTypeArgument("Int"))
        }

        assertEquals(sessionBefore, sessionAfter)
        assert(sessionAfter.isValid)
    }

    fun testUnrelatedModuleInBlockChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val anotherFile = anotherModule.findSourceKtFile("another.kt")

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile, anotherFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val anotherVariableDeclaration = anotherFile.findDescendantOfType<KtVariableDeclaration> { it.name == "y" }!!
            anotherVariableDeclaration.setName("z")
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testUnrelatedModuleOutOfBlockChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val anotherFile = anotherModule.findSourceKtFile("another.kt")

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile, anotherFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val anotherFunction = anotherFile.findDescendantOfType<KtNamedFunction> { it.name == "another" }!!
            anotherFunction.addModifier(KtTokens.PRIVATE_KEYWORD)
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testUnrelatedModuleDependencyChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            anotherModule.addKotlinStdlib()
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testDependentModuleInBlockChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        another()
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        srcModule.addDependency(anotherModule)

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val anotherFile = anotherModule.findSourceKtFile("another.kt")

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile, anotherFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val anotherVariableDeclaration = anotherFile.findDescendantOfType<KtVariableDeclaration> { it.name == "y" }!!
            anotherVariableDeclaration.setName("z")
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testDependentModuleOutOfBlockChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        another()
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        srcModule.addDependency(anotherModule)

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val anotherFile = anotherModule.findSourceKtFile("another.kt")

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile, anotherFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val anotherFunction = anotherFile.findDescendantOfType<KtNamedFunction> { it.name == "another" }!!
            anotherFunction.addModifier(KtTokens.PRIVATE_KEYWORD)
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testDependentModuleDependencyChange() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>val x = 0
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val anotherModule = createModuleInTmpDir("another") {
            val anotherFile = FileWithText(
                "another.kt",
                """
                    fun another() {
                        val y = 1
                    }
                """.trimIndent()
            )
            listOf(anotherFile)
        }

        srcModule.addDependency(anotherModule)

        val srcFile = srcModule.findSourceKtFile("test.kt")
        val variableDeclaration = srcFile.findDescendantOfType<KtVariableDeclaration> { it.name == "x" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "foo()", "", variableDeclaration)
        ensureFilesResolved(codeFragment, srcFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            anotherModule.addKotlinStdlib()
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    private fun performModification(element: KtElement, modificationBlock: () -> Unit): Pair<LLFirSession, LLFirSession> {
        val sessionBefore = getSession(element)
        project.executeWriteCommand("Context modification") {
            modificationBlock()
        }
        val sessionAfter = getSession(element)
        return Pair(sessionBefore, sessionAfter)
    }

    private fun getSession(element: KtElement): LLFirSession {
        val module = ProjectStructureProvider.getModule(project, element, contextualModule = null)
        val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSession(module)
        return resolveSession.getSessionFor(module)
    }
}